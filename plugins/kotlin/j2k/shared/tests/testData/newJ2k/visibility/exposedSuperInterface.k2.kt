// ERROR: 'public' sub-interface exposes its 'internal' supertype 'Base'.
internal interface Base {
    fun test()
}

interface Test : Base
