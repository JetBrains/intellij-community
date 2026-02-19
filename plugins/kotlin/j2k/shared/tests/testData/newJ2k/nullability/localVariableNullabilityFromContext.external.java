public class J {
    // A chain of method calls to confuse the Java DFA.
    // From Kotlin's POV the return type is "platform" (so, it can be used as not-null)
    Integer getInt() {
        return getInt2();
    }

    private Integer getInt2() {
        return getInt3();
    }

    private Integer getInt3() {
        return 42;
    }
}
