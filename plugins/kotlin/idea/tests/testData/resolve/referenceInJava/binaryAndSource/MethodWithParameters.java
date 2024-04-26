public class MethodWithParameters {
    public static void foo() {
        (new k.Class()).f<caret>unction2();
    }
}

// REF: (in k.Class).function2(Byte, Char, Short, Int, Long, Boolean, Float, Double, ByteArray, CharArray, IntArray, LongArray, BooleanArray, FloatArray, DoubleArray, Array<String>, Array<ByteArray>, Array<Array<String>>, T, G, String, F, G, F)
// CLS_REF: (in k.Class).function2(Byte, Char, Short, Int, Long, Boolean, Float, Double, ByteArray, CharArray, IntArray, LongArray, BooleanArray, FloatArray, DoubleArray, Array<String>, Array<ByteArray>, Array<Array<String>>, T, G, String, F, G, F)
