fun main(args: Array<String>) {
    val a = args.getOrNull(0) ?: <selection>throw IllegalStateException("Call me with two arguments")</selection>

    println(a.length)
}