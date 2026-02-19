fun main() {
    for (i in 1..10) {
        println(i)
        <selection>if (i == 3) {
            run {
                continue
            }
        }</selection>
        println("hi")
    }
}

// IGNORE_K1