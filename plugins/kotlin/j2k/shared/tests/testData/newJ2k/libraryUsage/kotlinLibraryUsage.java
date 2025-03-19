// WITH_LIBRARY: ../../libSources/simple/kotlin

import library.kotlin.test.LibraryKt;

public class J {
    void test() {
        LibraryKt lib = new LibraryKt();
        lib.doSomething("test");
    }
}
