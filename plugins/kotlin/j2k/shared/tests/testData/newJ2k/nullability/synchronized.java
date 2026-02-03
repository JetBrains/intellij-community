public class J {
    void foo(Object notNull) {
        synchronized(notNull) {
        }
    }
}
