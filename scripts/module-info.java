/**
 * Module with Implementor - implementation of JarImpl interface
 *
 * @author garipovroma
 */
module info.kgeorgiy.ja.garipov.implementor {
    requires transitive info.kgeorgiy.java.advanced.implementor;
    requires transitive info.kgeorgiy.java.advanced.base;
    requires java.compiler;

    exports info.kgeorgiy.ja.garipov.implementor;

    opens info.kgeorgiy.ja.garipov.implementor to junit;
}
