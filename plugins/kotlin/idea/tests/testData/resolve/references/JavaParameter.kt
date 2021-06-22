lateinit var x: java.lang.Readable
lateinit var y: java.nio.CharBuffer

val h = x.read(c<caret>b = y)

// REF: CharBuffer cb
