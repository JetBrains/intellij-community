interface Simple {
    val prop: String?
}

fun Simple.test(text: String): String {
    return buildString {
        append(text)


        <selection>append("Start")

        when {
            text.isEmpty() -> append(" empty")
            else -> append(" not empty")
        }

        val value = prop
        if (value != null) {
            append(" with ")
            append(value)
        } else {
            append(" without value")
        }

        append(".")</selection>
    }
}
// IGNORE_K1