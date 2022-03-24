package one.two

fun usage() {
    with(KotlinObject.NestedObject) { Receiver().staticExtension(3) }
}