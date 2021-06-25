package info.kgeorgiy.ja.garipov.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.HashSet;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import javax.tools.*;

/**
 * Produces code implementing class or interface specified by provided {@code token}.
 * <p>
 * Generates class classes name will be same as classes name of the type token with {@code Impl} suffix
 * added. Generated source code will be placed in the correct subdirectory of the specified
 * {@code root} directory and have correct file name.
 * @author garipov.roma (devilgar@gmail.com)
 */
public class Implementor implements JarImpler {

    /**
     * {@code Impl} suffix for implemented class name
     */
    private static final String CLASS_SUFFIX = "Impl";
    /**
     * Constant with java file extension string
     */
    private static final String JAVA_FILE_EXTENSION = ".java";
    /**
     * Constant with java class file extension string
     */
    private static final String CLASS_FILE_EXTENSION = ".class";
    /**
     * Inner {@link BufferedWriter} using for print generated code there
     */
    private BufferedWriter writer;

    // :NOTE: no cli interface
    // :NOTE: fixed

    /**
     * Main method for cli using Implementor
     *
     * @param args array containing full name for class/interface to generate implementation for it
     *             and directory where locate this implementation
     */
    public static void main(String[] args) {
        if (args == null || args.length < 2 || args[0] == null || args[1] == null) {
            System.err.println("Implementor requires 2 non-null arguments : className, root");
            return;
        }
        int ind = 0;
        if ("-jar".equals(args[0])) {
            ind++;
        }
        Class<?> clazz;
        try {
            clazz = Class.forName(args[ind]);
        } catch (ClassNotFoundException e) {
            System.err.println("Cannot find class " + args[ind] + " : " + e.getMessage());
            return;
        }
        Path root = null;
        try {
            root = Path.of(args[ind + 1]);
        } catch (InvalidPathException e) {
            System.err.println("Invalid path : " + e.getMessage());
            // :NOTE-2: no return
            return;
        }
        Implementor implementor = new Implementor();
        try {
            if (ind == 0) {
                implementor.implement(clazz, root);
            } else {
                implementor.implementJar(clazz, root.resolve(clazz.getSimpleName()).resolve(".jar"));
            }
        } catch (ImplerException e) {
            System.err.println(e.getMessage());
        }
    }

    // :NOTE: they are way too semantically different
    // :NOTE: fixed

    /**
     * Prints parameter types named following in brackets : (Type0 arg0, ...) to writer.
     *
     * Prints parameter types which is {@link Class#getCanonicalName()} and names following in brackets :
     * (Type0 arg0, ...) to writer. {@see BufferedWriter} writer should be created in impl method.
     * Otherwise, if you calling this method not from impl,
     * NullPointerException will thrown
     *
     * @param arguments arguments to print their {@link Class#getCanonicalName()} and names
     * @throws IOException throws from writer, then writing error occurred
     */
    private void printParameterTypesAndNames(Class<?>[] arguments) throws IOException {
        write("(");
        for (int i = 0; i < arguments.length; i++) {
            write(arguments[i].getCanonicalName() + " ");
            write("arg" + i);
            if (i != arguments.length - 1) {
                write("," + " ");
            }
        }
        write(")");
    }

    /**
     * Writes given string to {@link BufferedWriter} writer.
     * @param str String to write
     * @throws IOException if an error occured
     */
    private void write(String str) throws IOException {
        writer.write(stringToUnicode(str));
    }
    
    /**
     * Prints all given exceptions and word 'throws' to writer.
     * @param exceptions exceptions to print
     * @throws IOException throws from writer, then writing error occurred
     */
    private void printExceptions(Class<?>[] exceptions) throws IOException {
        if (exceptions.length == 0) {
            return;
        }
        write(" throws ");
        for (int i = 0; i < exceptions.length; i++) {
            write(exceptions[i].getCanonicalName() + " ");
            if (i != exceptions.length - 1) {
                write("," + " ");
            }
        }
    }

    /**
     * Prints constructor body with calling super with given arguments to writer
     * @param arguments - arguments to call super constructor
     * @throws IOException throws from writer, then writing error occurred
     */
    private void printConstructorBody(Class<?>[] arguments) throws IOException {
        write(" { super(");
        for (int i = 0; i < arguments.length; i++) {
            write("arg" + i);
            if (i != arguments.length - 1) {
                write("," + " ");
            }
        }
        write("); }\n\n");
    }

    /**
     * Prints print method signature to writer
     * @param method - method to write it signature
     * @throws IOException throws from writer, then writing error occurred
     */
    private void printMethodSignature(Method method) throws IOException {
        printParameterTypesAndNames(method.getParameterTypes());
        printExceptions(method.getExceptionTypes());
    }

    /**
     * Returns default value for class by it class token
     * @param token given token to return it default value
     * @return if token is token for boolean - false, if token is primitive - 0, otherwise - null
     */
    private String getDefaultValue(Class<?> token) {
        if (!token.isPrimitive()) {
            return "null";
        } else if (token.equals(boolean.class)) {
            return "false";
        } else {
            return "0";
        }
    }

    /**
     * Prints default method implementation by it returnType
     * @param returnType class token for value which method is returning
     * @throws IOException throws from writer, then writing error occurred
     */
    private void printDefaultMethodImplementation(Class<?> returnType) throws IOException {
        write("{");
        if (!returnType.equals(void.class)) {
            write(" " + "return" + " " + getDefaultValue(returnType) + ";" + " ");
        }
        write("}" + System.lineSeparator());
    }

    /**
     * Prints return type for given class token of some method to writer
     * @param returnType class token for method return type
     * @throws IOException throws from writer, then writing error occurred
     */
    private void printReturnType(Class<?> returnType) throws IOException {
        if (returnType.equals(void.class)) {
            write("void");
        } else {
            write(returnType.getCanonicalName());
        }
    }

    /**
     * Prints modifiers by int from {@link Class#getModifiers()}, {@link Method#getModifiers()} or {@link Constructor#getModifiers()} to writer.
     * @param modifiers modifiers from {@link Class#getModifiers()}, {@link Method#getModifiers()} or {@link Constructor#getModifiers()}
     * @throws IOException throws from writer, then writing error occurred
     */
    private void printModifiers(int modifiers) throws IOException {
        if (Modifier.isProtected(modifiers)) {
            write("protected" + " ");
        } else if (Modifier.isPublic(modifiers)) {
            write("public" + " ");
        }
    }

    /**
     * Prints method by {@link Method} class to writer.
     * @param method given {@link Method} to generate it implementation
     * @throws IOException throws from writer, then writing error occurred
     */
    private void printMethod(Method method) throws IOException {
        write("\t");
        printModifiers(method.getModifiers());
        Class<?> returnType = method.getReturnType();
        printReturnType(returnType);
        write(" ");
        write(method.getName());
        printMethodSignature(method);
        printDefaultMethodImplementation(returnType);
        write(System.lineSeparator());
    }

    /**
     * Prints methods {@link Method} classes array to writer.
     * @param methods given array of {@link Method} to generate it implementation
     * @throws IOException throws from writer, then writing error occurred
     */
    private void printMethods(Method[] methods) throws IOException {
        for (Method method: methods) {
            printMethod(method);
        }
        write("}" + System.lineSeparator());
    }

    /**
     * Prints implementation of given interface represented in given {@link Class} to writer
     * @param token class token of interface to generate it implementation
     * @throws IOException throws from writer, then writing error occurred
     */
    private void printInterfaceImplementationMethods(Class<?> token) throws IOException {
        // :NOTE: you can't implement static methods
        // :NOTE: fixed
        printMethods(Arrays.stream(token.getMethods())
                .filter((x -> Modifier.isAbstract(x.getModifiers())))
                .toArray(Method[]::new));
    }

    /**
     * Prints package of given {@link Class} to writer.
     * @param token class token to print it package
     * @throws IOException throws from writer, then writing error occurred
     */
    private void printPackage(Class<?> token) throws IOException {
        // :NOTE: package name might be ""
        // :NOTE: fixed
        if (!token.getPackageName().isEmpty()) {
            write("package" + " " + token.getPackageName() + ";" + System.lineSeparator());
        }
        write(System.lineSeparator());
    }

    /**
     * Prints class declaration for {@link Class} to writer.
     * @param token to generate declaration
     * @param classExtends true if 'extends' word is necessary, false if 'implements'
     * @throws IOException throws from writer, then writing error occurred
     */
    private void printClassDeclaration(Class<?> token, boolean classExtends) throws IOException {
        printModifiers(Modifier.isPublic(token.getModifiers()) ? Modifier.PUBLIC : 0);
        write("class" + " " + token.getSimpleName() + CLASS_SUFFIX
                + " " + (classExtends ? "extends" : "implements") + " " + token.getCanonicalName());
        write(" " + "{" + System.lineSeparator() + System.lineSeparator());
    }

    /**
     * Prints {@link Constructor} implementation for given {@link Class}
     * @param token given {@link Class}
     * @param constructor given {@link Constructor}
     * @throws IOException
     */
    private void printConstructor(Class<?> token, Constructor<?> constructor) throws IOException {
        write("\t");
        printModifiers(constructor.getModifiers());
        write(token.getSimpleName() + CLASS_SUFFIX);
        printParameterTypesAndNames(constructor.getParameterTypes());
        // :NOTE: please, understand why you do this
        printExceptions(constructor.getExceptionTypes());
        printConstructorBody(constructor.getParameterTypes());
    }

    /**
     * Prints constructors of given {@link Class} to writer
     * @param token class token to print it constructors
     * @throws IOException throws from writer, then writing error occurred
     */
    private void printConstructors(Class<?> token) throws IOException {
        Constructor<?>[] constructors = token.getDeclaredConstructors();
        for (Constructor<?> constructor: constructors) {
            printConstructor(token, constructor);
        }
    }

    /**
     * Prints methods of extensions of given {@link Class} to writer.
     * @param token to generate if methods implementation
     * @throws IOException throws from writer, then writing error occurred
     */
    private void printClassExtensionMethods(Class<?> token) throws IOException {
        HashSet<MethodWrapper> methodsHashSet = new HashSet<>();
        for (Class<?> clazz = token; clazz != null; clazz = clazz.getSuperclass()) {
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                MethodWrapper methodWrapper = new MethodWrapper(method);
                methodsHashSet.add(methodWrapper);
            }
        }
        assert token != null;
        Method[] allMethods = token.getMethods();
        for (Method method : allMethods) {
            MethodWrapper methodWrapper = new MethodWrapper(method);
            methodsHashSet.add(methodWrapper);
        }
        Method[] suitableMethods = methodsHashSet.stream()
                .map((MethodWrapper x) -> x.method)
                .filter((Method x) -> Modifier.isAbstract(x.getModifiers()))
                .collect(Collectors.toList())
                .toArray(Method[]::new);
        printMethods(suitableMethods);
    }

    /**
     * Checks input of {@link Implementor#implement(Class, Path)}
     * @param token token from {@link Implementor#implement}
     * @param root root from {@link Implementor#implement}
     * @throws ImplerException
     */
    private void checkImplementInput(Class<?> token, Path root) throws ImplerException {
        if (token == null) {
            throw new ImplerException("token can't be null");
        }
        if (root == null) {
            throw new ImplerException("root can't be null");
        }
        if (token.isPrimitive()) {
            throw new ImplerException("Cannot implement primitive");
        }
        // :NOTE: this does not catch enums, actually
        // :NOTE: fixed
        if (token.isEnum() || token == Enum.class) {
            throw new ImplerException("Cannot implement Enum");
        }
        if (Modifier.isFinal(token.getModifiers())) {
            throw new ImplerException("Cannot implement class from final class");
        }
        if (Modifier.isPrivate(token.getModifiers())) {
            throw new ImplerException("Cannot implement private class");
        }
        if (token.getDeclaredConstructors().length != 0 &&
                Arrays.stream(token.getDeclaredConstructors()).allMatch((x -> Modifier.isPrivate(x.getModifiers())))) {
            throw new ImplerException("Cannot implement, because there is no accessible constructor");
        }
    }

    /**
     * Resolves path to generated for this token file implementation
     * @param token token of class for implementation generation
     * @param root root from {@link Implementor#implement} method
     * @param extension file extension to print implementation there
     * @return Path to file with implementation
     */
    private Path resolvePath(Class<?> token, Path root, String extension) {
        // :NOTE: not cross-platform
        // :NOTE: fixed
        return root
                .resolve(token.getPackageName().replace(".", File.separator))
                .resolve(token.getSimpleName() + CLASS_SUFFIX + extension);
    }

    /**
     * Realization of {@link info.kgeorgiy.java.advanced.implementor.Impler#implement(Class, Path)}
     * @param token type token to create implementation for.
     * @param root root directory.
     * @throws ImplerException if can't generate implementation.
     */
    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        checkImplementInput(token, root);
        Path path = resolvePath(token, root, JAVA_FILE_EXTENSION);
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            throw new ImplerException("Cannot create directory " + path, e);
        }
        try (BufferedWriter newBufferedWriter = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer = newBufferedWriter;
            printPackage(token);
            printClassDeclaration(token, !token.isInterface());
            if (token.isInterface()) {
                printInterfaceImplementationMethods(token);
            } else {
                printConstructors(token);
                printClassExtensionMethods(token);
            }
        } catch (IOException e) {
            throw new ImplerException("Cannot create and write to file", e);
        }
    }

    /**
     * Realization of {@link JarImpler#implementJar(Class, Path)}
     * @param token type token to create implementation for.
     * @param jarFile target <var>.jar</var> file.
     * @throws ImplerException if can't generate implementation.
     */
    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        Path jarFileParent = jarFile.getParent();
        try {
            Files.createDirectories(jarFile.getParent());
        } catch (IOException e) {
            throw new ImplerException("Cannot create directory " + jarFile, e);
        }
        Path temporaryDirectory;
        try {
            temporaryDirectory = Files.createTempDirectory(jarFileParent, "TMP_IMPLER_DIR");
        } catch (IOException e) {
            throw new ImplerException("Cannot create temporary directory", e);
        }
        try {
            implement(token, temporaryDirectory);
            compile(token, temporaryDirectory);
            createJar(token, jarFile, temporaryDirectory);
        }
        finally {
            try {
                Files.walkFileTree(temporaryDirectory, FILES_REMOVER);
            } catch (IOException e) {
                throw new ImplerException("Cannot remove temporary directory", e);
            }
        }
    }

    /**
     * Compiles implementation({@link Implementor#implement(Class, Path)}) in file in directory
     * @param token class token to compile
     * @param directory directory where compiled file should be
     * @throws ImplerException if can't generate implementation.
     */
    private void compile(Class<?> token, Path directory) throws ImplerException {
        Path classPath;
        CodeSource codeSource = token.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            classPath = Path.of("");
        } else {
            try {
                classPath = Path.of(codeSource.getLocation().toURI().toString());
            } catch (URISyntaxException e) {
                throw new ImplerException("Failed to get classpath for compiling", e);
            }
        }
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        if (javaCompiler == null) {
            throw new ImplerException("Cannot compile implemented class, compiler not found");
        }
        String[] arguments = {"-cp",
                classPath.toString(),
                resolvePath(token, directory, JAVA_FILE_EXTENSION).toString()};
        if (codeSource == null) {
            arguments = new String[]{"--patch-module", token.getModule().getName() + "=" + directory,
                    resolvePath(token, directory, JAVA_FILE_EXTENSION).toString()};
        }
        int code = javaCompiler.run(null, null, null, arguments);
        if (code != 0) {
            throw new ImplerException("An error occurred during implemented file compilation");
        }
    }

    /**
     * Creates jar file of given {@link Class} in {@code directory}
     * @param token give token to create jar
     * @param jarFile path to jar file
     * @param directory directory to create jar
     * @throws ImplerException if can't generate implementation.
     */
    private void createJar(Class<?> token, Path jarFile, Path directory) throws ImplerException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
            // :NOTE-2: slash is a separator in jar files
            jarOutputStream.putNextEntry(
                    new ZipEntry(resolvePath(token, Path.of(""), CLASS_FILE_EXTENSION).toString()));
            Files.copy(resolvePath(token, directory, CLASS_FILE_EXTENSION), jarOutputStream);
        } catch (IOException e) {
            throw new ImplerException("Cannot create jar file", e);
        }
    }

    private String stringToUnicode(String str) {
        StringBuilder result = new StringBuilder();
        char[] charArray = str.toCharArray();
        for (char x : charArray) {
            if (x < 128) {
                result.append(x);
            } else {
                result.append(String.format("\\u%04x", (int) x));
            }
        }
        return result.toString();
    }

    /**
     * Removes files recursively. It's analog of rm -r -f *directory*
     */
    private static final FileVisitor<Path> FILES_REMOVER = new SimpleFileVisitor<>() {
        /**
         * PostVisitDirectory method from {@link SimpleFileVisitor#postVisitDirectory(Object, IOException)}
         * @param dir current dir
         * @param exc exception
         * @return FileVisitResult {@see FileVisitResult}
         * @throws IOException then can't delete dir
         */
        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }

        /**
         * PostVisitDirectory method from {@link SimpleFileVisitor#visitFile(Object, BasicFileAttributes)}
         * @param file current file
         * @param attrs attributes
         * @return FileVisitResult {@see FileVisitResult}
         * @throws IOException then can't delete file
         */
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }
    };

    /**
     * Wrapper for {@link Method} to hold it in {@link HashSet}
     */
    private static class MethodWrapper {

        /**
         * Method to wrap
         */
        private final Method method;

        /**
         * Simple constructor for MethodWrapper
         * @param method to wrap
         */
        public MethodWrapper(Method method) {
            this.method = method;
        }

        /**
         * Overrided equals method
         * @param object object to check equality
         * @return true if object is equal, false otherwise
         */
        @Override
        public boolean equals(Object object) {
            if (!(object instanceof MethodWrapper)) {
                return false;
            }
            MethodWrapper methodWrapper = (MethodWrapper) object;
            return method.getName().equals(methodWrapper.method.getName()) &&
                    Arrays.equals(method.getParameterTypes(), methodWrapper.method.getParameterTypes());
        }

        /**
         * Calculates hashCode
         * @see "https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/Object.html#hashCode()"
         * @return hashCode of this object
         */
        @Override
        public int hashCode() {
            return 31 * method.getName().hashCode() + Arrays.hashCode(method.getParameterTypes());
        }
    }
}
