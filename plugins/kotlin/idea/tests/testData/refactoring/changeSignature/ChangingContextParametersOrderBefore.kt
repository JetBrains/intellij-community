// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
interface ToJson<T> {
    fun toJson(thing: T): String
}

interface Logger {
    fun log(message: String)
}

interface ErrorHandler {
    fun handle(error: Throwable)
}

context(js<caret>on: ToJson<T>, logger: Logger, handler: ErrorHandler)
fun <T> T.multipleContextTest(additionalInfo: String = ""): String {
    return ""
}