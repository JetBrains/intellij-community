// WITH_LIBRARY: /simple/kotlin

import library.kotlin.test.LibraryKt;

public class J {
    void test() {
        LibraryKt lib = new LibraryKt();
        lib.doSomething("test");
    }
}
