// ERROR: Sub-interface 'public' exposes its 'internal' supertype 'Base'.
internal interface Base {
    fun test()
}

interface Test : Base
