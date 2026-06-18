package sample

class Target

context(t: Target)
fun Target.<caret>foo() {
    println(t)
    println(this) // KTIJ-39211
}
