class J {
    fun foo(j: J) {
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
}
