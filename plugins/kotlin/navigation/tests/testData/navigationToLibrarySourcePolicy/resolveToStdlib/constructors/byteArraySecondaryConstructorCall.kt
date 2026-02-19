fun a() {
    val array = Byte<caret>Array(10) { 1.toByte() }
}

// REF: (kotlin.ByteArray @ jar://kotlin-stdlib-sources.jar!/jvmMain/kotlin/Arrays.kt) @Suppress("WRONG_MODIFIER_TARGET")     public actual inline constructor(size: Int, init: (Int) -> Byte)