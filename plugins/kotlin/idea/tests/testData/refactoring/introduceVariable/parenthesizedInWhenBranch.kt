// REPLACE_SINGLE_OCCURRENCE
fun foo(flag: Boolean): String = when (true) {
    false -> <selection>("1")</selection>
    true -> "1"
}

// IGNORE_K1