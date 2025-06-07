
@OptIn(ExperimentalStdlibApi::class)
fun f() {
    val b: HexFormat.BytesHexFormat? = null
    b?.bytesPe<caret>rLine
}

// REF: (kotlin.text.HexFormat.BytesHexFormat @ jar://kotlin-stdlib-sources.jar!/commonMain/kotlin/text/HexFormat.kt) public val bytesPerLine: Int