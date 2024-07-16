fun foo() {
    listOf(1, "a").mapNotNull {
        <selection>run {
            return@mapNotNull 1
        }</selection>
    }
}

// IGNORE_K1