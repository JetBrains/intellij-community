import kotlin.text.MatchGroup
fun bbb(mg: MatchGroup) {
    val s = mg.<caret>value
}

// REF: (kotlin.text.MatchGroup.value) public actual val value: String