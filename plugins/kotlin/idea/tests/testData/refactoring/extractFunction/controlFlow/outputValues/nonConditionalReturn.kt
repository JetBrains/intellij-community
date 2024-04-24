class Foobar {
    inline fun forEach(action: () -> Unit): Unit {

    }

    fun test() {
        <selection>if (2 > 3) return
        forEach {
            if (3 > 5) return@forEach
            if (5 > 6) return
        }
        System.out.println(2)</selection>
    }
}

// IGNORE_K1