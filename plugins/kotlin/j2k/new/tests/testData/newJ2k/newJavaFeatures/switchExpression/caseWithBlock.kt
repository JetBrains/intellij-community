fun foo(a: Int): Int {
    return when (a) {
        1 -> {
            val x = 1
            println(x)
            1
        }

        2 -> {
            val x = 2
            println(x)
            2
        }

        3 -> {
            println(3)
        }
    }
}
