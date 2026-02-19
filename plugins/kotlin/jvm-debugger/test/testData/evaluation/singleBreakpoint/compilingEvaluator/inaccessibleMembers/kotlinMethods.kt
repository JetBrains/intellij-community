// FILE: test.kt
package foo

class KotlinClass {
    public companion object {
        @JvmStatic
        fun s1(a: Int, b: Long, c: String): Double = -1.0

        @JvmStatic
        fun s2(): String = "foo"

        @JvmStatic
        fun s3(c: Char, s: String, b: Boolean): Int = 2
    }

    fun i1(a: Int, b: Long, c: String): Long = -2
    fun i2(): String = "bar"
    fun i3(c: Char, s: String, b: Boolean): Int = 4
}

interface KotlinInterface {
    companion object {
        @JvmStatic
        fun s1(): String = "y"
    }
}

class KotlinClass2 {
    companion object Comp {
        fun s1(a: Int, b: Long, c: String): Double = -1.0
        fun s2(): String = "fooz"
        fun s3(c: Char, s: String, b: Boolean): Int = 2
    }
}

fun main() {
    val k1 = KotlinClass()
    val k2 = KotlinClass2()
    //Breakpoint!
    val a = 0
}

fun <T> block(block: () -> T): T {
    return block()
}

// EXPRESSION: block { KotlinClass.s1(1, 2, "c") }
// RESULT: -1.0: D

// EXPRESSION: block { KotlinClass.s2() }
// RESULT: "foo": Ljava/lang/String;

// EXPRESSION: block { KotlinClass.s3('c', "s", true) }
// RESULT: 2: I

// EXPRESSION: block { k1.i1(5, 10L, "x") }
// RESULT: -2: J

// EXPRESSION: block { k1.i2() }
// RESULT: "bar": Ljava/lang/String;

// EXPRESSION: block { k1.i3('q', "z", false) }
// RESULT: 4: I

// EXPRESSION: block { KotlinInterface.s1() }
// RESULT: "y": Ljava/lang/String;

// EXPRESSION: block { KotlinClass2.s1(1, 2, "c") }
// RESULT: -1.0: D

// EXPRESSION: block { KotlinClass2.s2() }
// RESULT: "fooz": Ljava/lang/String;

// EXPRESSION: block { KotlinClass2.s3('v', "x", false) }
// RESULT: 2: I

// EXPRESSION: block { KotlinClass2.Comp.s1(1, 2, "c") }
// RESULT: -1.0: D

// EXPRESSION: block { KotlinClass2.Comp.s2() }
// RESULT: "fooz": Ljava/lang/String;

// EXPRESSION: block { KotlinClass2.Comp.s3('v', "x", false) }
// RESULT: 2: I