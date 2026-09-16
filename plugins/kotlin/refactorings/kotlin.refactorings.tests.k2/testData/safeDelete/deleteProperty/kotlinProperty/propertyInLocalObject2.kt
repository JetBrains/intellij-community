open class SampleParent

fun context() {
    val v = object : SampleParent() { var <caret>addition = 0 }

    println(v.addition)
    println(v.addition)
}