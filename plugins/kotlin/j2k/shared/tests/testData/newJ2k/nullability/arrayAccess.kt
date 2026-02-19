class Foo {
    private fun test(q: IntArray, q2: IntArray, i: Int) {
        println(q[0])
        println(q[i])
        q2[0] = 42
    }

    private fun test2(q: IntArray, q2: IntArray, i: Int) {
        for (j in 10 downTo 0) {
            // empty loop that revealed a bug in nullity inferrer for some reason (?)
        }

        println(q[0])
        println(q[i])
        q2[0] = 42
    }
}
