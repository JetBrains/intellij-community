// IGNORE_K2
// RUNTIME_WITH_FULL_JDK

@FunctionalInterface
public interface MyRunnable {
    <T> void process(T t);
}