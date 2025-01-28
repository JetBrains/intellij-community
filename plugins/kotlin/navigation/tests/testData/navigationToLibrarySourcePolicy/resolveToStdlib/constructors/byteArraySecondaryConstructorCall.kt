fun a() {
    val array = Byte<caret>Array(10) { 1.toByte() }
}

// REF: (<local>) @Suppress("WRONG_MODIFIER_TARGET")     public inline constructor(size: Int, init: (Int) -> Byte)