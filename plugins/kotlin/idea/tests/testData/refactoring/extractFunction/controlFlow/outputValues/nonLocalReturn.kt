inline fun loop(block: () -> Unit): Nothing { while(true) block() }

fun foo(): Boolean {
    <selection>loop {
        return true
    }</selection>
}

// IGNORE_K1