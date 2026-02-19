import kotlin.text.MatchGroup
fun bbb(mg: MatchGroup) {
    val s = mg.<caret>value
}

// REF: (kotlin.text.MatchGroup @ jar://kotlin-stdlib-sources.jar!/jvmMain/kotlin/text/regex/Regex.kt) public actual val value: String