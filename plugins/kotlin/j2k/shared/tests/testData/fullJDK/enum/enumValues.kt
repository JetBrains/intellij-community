import java.lang.reflect.Array
import java.util.Arrays
import java.util.List

internal enum class MyEnum {
    A,
    B;

    @ExperimentalStdlibApi
    fun internalTest() {
        val x = entries[1]
        val y = entries.toTypedArray()
    }
}

internal class EnumTest {
    fun replaceWithEntries() {
        val x = MyEnum.entries[1]

        // Array methods suitable for List
        val z = MyEnum.entries.size

        // References
        for (value in MyEnum.entries) {
        }
    }

    fun replaceWithEntriesWithConversionToArray() {
        // Simple call
        val x = MyEnum.entries.toTypedArray()

        // Object methods
        val o = MyEnum.entries.toTypedArray().toString()

        // Operator method
        MyEnum.entries.toTypedArray()[1] = MyEnum.A

        // Array methods not suitable for List
        MyEnum.entries.toTypedArray().clone()
        val y = MyEnum.entries.toTypedArray().contentToString()
        Array.get(MyEnum.entries.toTypedArray(), 1)

        // Stream methods
        val s = Arrays.stream(MyEnum.entries.toTypedArray())

        // Iterator methods
        val i1 = Arrays.stream(MyEnum.entries.toTypedArray()).iterator()
        val i2 = Arrays.stream(MyEnum.entries.toTypedArray()).spliterator()

        // This case is not handled
        val z = List.of(*MyEnum.entries.toTypedArray())
    }
}
