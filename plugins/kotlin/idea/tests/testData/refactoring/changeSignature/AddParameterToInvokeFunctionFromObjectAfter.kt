object InvokeObject {
    operator fun invoke(i: Int) {}
}

val invokeObjectCall = InvokeObject(42)