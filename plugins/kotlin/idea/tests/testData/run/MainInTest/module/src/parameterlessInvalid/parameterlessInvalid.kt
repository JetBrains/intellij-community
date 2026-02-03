// entryPointExists
// NO-DUMB-MODE

package parameterlessInvalid

fun main() { // no
}

@JvmName("main")
fun notMain(args: Array<String>) { // yes
}
