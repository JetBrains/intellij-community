class Foreign {
}

public abstract class Test1 {

    /**
     * @param f
     */
    void <caret>foo (Foreign f) {
    }

    /**
     * @see #foo(Foreign)
     */
    void bar () {
    }
}