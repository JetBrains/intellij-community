class J {
    fun simple(j: J) {
        (this as Object).notify()
        (this as Object).notifyAll()
        (this as Object).wait()
        (this as Object).wait(42)
        (this as Object).wait(42, 42)
        (j as Object).notify()
        (j as Object).notifyAll()
        (j as Object).wait()
        (j as Object).wait(42)
        (j as Object).wait(42, 42)
    }

    fun withCast(i: Number) {
        (i as Int as Object).notify()
        (i as Object).notifyAll()
        (i as Object).wait()
        (i as Object).wait(42)
        (i as Object).wait(42, 42)
        (i as Object).notify()
        (i as Object).notifyAll()
        (i as Object).wait()
        (i as Object).wait(42)
        (i as Object).wait(42, 42)
    }
}
