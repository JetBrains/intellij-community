object AAAA {
    operator fun inc(): AAAA = this
}

fun test() {
    AAAA+<caret>+
}

// REF: (in AAAA).inc()