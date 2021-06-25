package info.kgeorgiy.ja.garipov.walk;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;

public class Walker {
    public static void walk(String[] args, int depth) {
        if (args == null || args.length < 2 || (args.length == 2 && (args[0] == null || args[1] == null))) {
            System.err.println("Invalid arguments format");
        } else {
            try {
                Path path = Path.of(args[1]);
                if (path.getParent() != null) {
                    Files.createDirectory(path.getParent());
                }
            } catch (FileAlreadyExistsException e) {
                // ignored
            } catch (IOException e) {
                System.err.println("[output error] An error occured while creating directory for output file \"" + args[1] + "\" : " + e);
                return;
            } catch (InvalidPathException e) {
                System.err.println("[output error] Invalid path for output file \"" + args[1] + "\" : " + e);
                return;
            }
            final HashWritingFileVisitor fileVisitor = new HashWritingFileVisitor(args[1]);
            try (BufferedReader reader = Files.newBufferedReader(Path.of(args[0]))) {
                String filename;
                while ((filename = reader.readLine()) != null) {
                    try {
                        Files.walkFileTree(Path.of(filename), Collections.singleton(FileVisitOption.FOLLOW_LINKS), depth,
                                fileVisitor);
                    } catch (InvalidPathException e) {
                        printOutput(args[1], filename, 0);
                    } catch (IOException e) {
                        System.err.println("[process error] An error occurred while walking files " + e);
                        printOutput(args[1], filename, 0);
                    }
                }
            } catch (NoSuchFileException e) {
                System.err.println("[input error] There is no such file \"" + args[0] + "\" : " + e);
            } catch (IOException e) {
                System.err.println("[input error] An error occurred while reading from \"" + args[0] + "\" : " + e);
            } catch (InvalidPathException e) {
                System.err.println("[input error] Invalid path \"" + args[0] + "\" : " + e);
            }
        }
    }

    public static void printOutput(String outputFilename, String file, long hash) {
        try {
            Files.write(Path.of(outputFilename),
                    Collections.singleton(String.format(HashWritingFileVisitor.stringFormat, hash) + " " + file),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[output error] An error occurred while writing to \"" + outputFilename + "\" : " + e);
        }
    }
}
