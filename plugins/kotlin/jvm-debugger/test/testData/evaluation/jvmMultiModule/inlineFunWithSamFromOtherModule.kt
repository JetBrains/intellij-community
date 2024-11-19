// MODULE: jvm-lib
// FILE: Condition.java
@FunctionalInterface
public interface Condition<T> {
    boolean test(T t);
}

// FILE: CustomIterable.java
import kotlin.NotImplementedError;

public class CustomIterable<E> {
    public static <E> CustomIterable<E> empty() {
        return new CustomIterable<>();
    }

    public final CustomIterable<E> filter(Condition<? super E> condition) {
        return this;
    }
}

// FILE: lib.kt

inline fun <reified E> findById() = CustomIterable.empty<E>().filter { true }

// MODULE: jvm-app(jvm-lib)
// FILE: main.kt

fun main() {
    // EXPRESSION: findById<Int>()
    // RESULT: instance of CustomIterable(id=ID): LCustomIterable;
    //Breakpoint!
    println()
}

// IGNORE_K2