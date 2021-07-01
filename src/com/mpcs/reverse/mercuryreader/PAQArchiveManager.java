package com.mpcs.reverse.mercuryreader;

import picocli.CommandLine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name="paqManager", mixinStandardHelpOptions = true, version = "1.0",
description = "Extracts & creates PAQ archives used in the PSP game Maclean's Mercury.")
public class PAQArchiveManager implements Callable<Integer> {
    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    ModeOptions modeOptions;

    static class ModeOptions {
        @CommandLine.Option(names = {"-x", "--extract"}, description = "Extract the specified PAQ archive")
        boolean extract;

        @CommandLine.Option(names = {"-a", "--archive"}, description = "Bundle files from the specified directory in a PAQ archive")
        boolean archive;
    }

    @CommandLine.Option(names = {"-d", "--debug"}, description = "Enable debug information")
    boolean debug;

    @CommandLine.Option(names = {"-s", "--silent"}, description = "Disable terminal output")
    boolean silent;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Select the output directory or file",  defaultValue = ".")
    String outputPath;

    @CommandLine.Parameters()
    String input;

    public static final byte[] fileSignature = {(byte)0xD1, (byte)0x07, 0, 0};

    public int amountOfFiles;
    public int tableLength;

    @Override
    public Integer call() throws Exception {
        final long startTime = System.nanoTime();
        File inputFile = new File(input);

        printDebug("Output: " + outputPath);
        printDebug("Input: " + input);
        printDebug("");

        int returnValue = 0;
        if (modeOptions.extract) {
            printDebug("Extraction Mode");
            if (inputFile.isDirectory()) {
                printInfo("Specified input is not a file.: " + input);
                return -1;
            }
            returnValue = extractArchive(input, outputPath);

        } else if (modeOptions.archive){
            printDebug("Archiving Mode");
            if (inputFile.isFile()) {
                printInfo("Specified input is not a directory.: " + input);
                return -1;
            }

            if (new File(outputPath).isDirectory()) {
                outputPath += "\\out.paq";
            }

            returnValue = repackArchive(input, outputPath);
        }
        if (returnValue >= 0) {
            final double duration = (System.nanoTime() - startTime) / 1000000.0;
            printInfo((modeOptions.archive ? "Archived" : "Extracted") + " " + amountOfFiles + " file(s) in " + duration + "ms");
        }
        return returnValue;
    }

    public static void main(String... args){
        int exitCode = new CommandLine(new PAQArchiveManager()).execute(args);
        System.exit(exitCode);
    }

    /** REPACKING **/
    public int repackArchive(String directory, String outputFilePath) throws IOException {
        List<FileDefinition> fileList = listFilesAndCreateTable(directory);

        FileOutputStream fos = new FileOutputStream(outputFilePath);
        fos.write(fileSignature);

        fos.write(intToBytes(amountOfFiles));
        fos.write(intToBytes(tableLength));
        fos.write(intToBytes(0));

        for (FileDefinition definition : fileList) {
            printDebug("Entering file: " + definition.toString());
            String hexString = definition.fileSignature;
            if(hexString.length() != 8 || !hexString.matches("^[a-fA-F0-9]+$")) {
                printInfo("Filename is a wrong length(8 needed) and/or is not a hexadecimal number. Aborting. Filename: " + hexString);
                return -1;
            }
            fos.write(hexStringToByteArray(hexString));
            fos.write(intToBytes(definition.length));
            fos.write(intToBytes(definition.offset));
            fos.write(intToBytes(definition.length));
        }

        for (FileDefinition definition : fileList) {
            fos.write(definition.data);
        }

        fos.close();
        return 0;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public List<FileDefinition> listFilesAndCreateTable(String directory) throws IOException {
        File folder = new File(directory);
        File[] listOfFiles = folder.listFiles();

        assert listOfFiles != null;
        tableLength = 0x10 * (listOfFiles.length + 1);
        amountOfFiles = listOfFiles.length;

        int lastDataOffset = tableLength;

        List<FileDefinition> fileList = new ArrayList<>();

        for (File file : listOfFiles) {
            if (file.isFile()) {
                FileDefinition fileDefinition = new FileDefinition(file.getName(), lastDataOffset, (int)file.length());
                FileInputStream fos = new FileInputStream(file.getAbsolutePath());

                fos.read(fileDefinition.data);
                fileList.add(fileDefinition);
                lastDataOffset += file.length();
            }
        }
        return fileList;
    }


    /** EXTRACTION **/

    public int extractArchive(String filePath, String outputDirectory) throws IOException {
        ArrayList<FileDefinition> fileTable = new ArrayList<>();

        RandomAccessFile file = new RandomAccessFile(filePath, "r");
        readHeader(file);

        readFileList(file, fileTable);
        unpackFiles(file, fileTable, outputDirectory);

        file.close();
        return 0;
    }

    public void readHeader(RandomAccessFile file) throws IOException {
        file.seek(4);

        byte[] amountOfFilesArr = new byte[4];
        file.read(amountOfFilesArr);

        amountOfFiles = (amountOfFilesArr[0] & 0xFF) | ((amountOfFilesArr[1] & 0xFF) << 8) | ((amountOfFilesArr[2] & 0x0F) << 16);

        byte[] tableLengthArr = new byte[4];
        file.read(tableLengthArr);

        tableLength = (tableLengthArr[0] & 0xFF) | ((tableLengthArr[1] & 0xFF) << 8) | ((tableLengthArr[2] & 0x0F) << 16);

    }
    public void readFileList(RandomAccessFile file, List<FileDefinition> fileDefinitionList) throws IOException {
        int currentPos = 0x10;
        file.seek(currentPos);
        for (int i = 0; i < amountOfFiles; i++) {
            byte[] fileName = new byte[4];
            byte[] fileSize = new byte[4];
            byte[] fileOffset = new byte[4];

            file.read(fileName);
            file.read(fileSize);
            file.read(fileOffset);

            FileDefinition def = new FileDefinition(fileName, fileOffset, fileSize);

            fileDefinitionList.add(def);
            currentPos = 0x10 + (i + 1) * 0x10;

            file.seek(def.offset);

            file.read(def.data);

            file.seek(currentPos);
        }
    }
    public void unpackFiles(RandomAccessFile file, List<FileDefinition> fileDefinitionList, String outputDirectory) throws IOException {
        for (FileDefinition definition : fileDefinitionList) {
            printDebug("Extracting file: " + definition.toString());
            FileOutputStream stream = new FileOutputStream(outputDirectory + "\\" + definition.fileSignature, false);

            byte[] fileData = new byte[definition.length];

            file.seek(definition.offset);
            file.read(fileData);

            stream.write(fileData);
            stream.close();
        }

    }


    /** UTIl **/
    private void printDebug(String string) {
        if (debug) {
            printInfo(string);
        }
    }

    private void printInfo(String string) {
        if (!silent) {
            System.out.println(string);
        }
    }

    /** Static Util **/
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
    public static byte[] intToBytes(int i) {
        byte[] bytes = new byte[4];

        bytes[0] = (byte)((i & 0xFF));
        bytes[1] = (byte)((i >> 8) & 0xFF);
        bytes[2] = (byte)((i >> 16) & 0xFF);
        bytes[3] = (byte)((i >> 24) & 0xFF);

        return bytes;
    }
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
