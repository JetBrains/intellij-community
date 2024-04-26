package q

// RUN: q.TestKt
fun main() {
    println("Top-level")
}

// RUN: q.Foo
object Foo {
    // RUN: q.Foo
    @JvmStatic fun main(args: Array<String>) {
        println("Foo")
    }
}
// RUN_FILE: q.TestKt