// FILE: genericCastToJavaClass.kt
package genericCastToJavaClass

fun main() {
    val m: JMarker = JBox("java")
    //Breakpoint!
    println()
}

// VARIABLE_PATH: m.size
// EVAL_EXPRESSION: (m as JBox<*>).size

// FILE: genericCastToJavaClass/JBox.java
package genericCastToJavaClass;

public class JBox<T> implements JMarker {
    public final T value;
    public final int size = 1;

    public JBox(T value) {
        this.value = value;
    }
}

// FILE: genericCastToJavaClass/JMarker.java
package genericCastToJavaClass;

public interface JMarker {}
