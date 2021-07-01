package com.mpcs.reverse.mercuryreader;

public class FileDefinition {
    public String fileSignature;
    public int offset;
    public int length;
    public byte[] data;

    public FileDefinition(byte[] signature, byte[] offset, byte[] length) {
        this(PAQArchiveManager.bytesToHexString(signature),
                (offset[0] & 0xFF) | ((offset[1] & 0xFF) << 8) | ((offset[2] & 0xFF) << 16) | ((offset[3]) << 24),
                (length[0] & 0xFF) | ((length[1] & 0xFF) << 8) | ((length[2] & 0xFF) << 16) | ((length[3]) << 24));
    }

    public FileDefinition(String fileSignature, int offset, int length) {
        this.fileSignature = fileSignature;
        this.offset = offset;
        this.length = length;
        this.data = new byte[this.length];
    }


    @Override
    public String toString() {
        return "[FileName: " + fileSignature +
                "  Offset: " + offset + "  Length: " + length + "]";
    }
}
