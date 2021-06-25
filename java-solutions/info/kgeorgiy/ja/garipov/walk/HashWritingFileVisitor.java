package info.kgeorgiy.ja.garipov.walk;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class HashWritingFileVisitor extends SimpleFileVisitor<Path> {
    private final String outputFilename;
    public final static int HASH_LENGTH = 16;
    public final static String stringFormat = "%016x";

    public HashWritingFileVisitor(String outputFilename) {
        this.outputFilename = outputFilename;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        long hash = 0;
        try (BufferedInputStream inputStream = new BufferedInputStream(Files.newInputStream(file))) {
            int inputInt;
            while ((inputInt = inputStream.read()) != -1) {
                byte inputByte = (byte) inputInt;
                hash <<= 8;
                hash += (inputByte & 0xff);
                long left = hash & 0xFF_00_000_000_000_000L;
                if (left != 0) {
                    hash ^= (left >> 48);
                    hash &= (~left);
                }
            }
        } catch (NoSuchFileException e) {
            System.err.println("[input error] There is no such file \"" + file + "\" : " + e);
            hash = 0;
        } catch (IOException e) {
            System.err.println("[input error] An error occurred while reading from \"" + file + "\" : " + e);
            hash = 0;
        } catch (InvalidPathException e) {
            System.err.println("[input error] Invalid path \"" + file + "\" : " + e);
            hash = 0;
        }
        Walker.printOutput(outputFilename, file.toString(), hash);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
        System.err.println("[input error] An error occurred while processing file \"" + file + "\" : " + exc);
        Walker.printOutput(outputFilename, file.toString(), 0);
        return FileVisitResult.CONTINUE;
    }
}

