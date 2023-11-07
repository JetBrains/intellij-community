class Context(val context: Int)

object Account {
    fun foo() : Context.() -> Unit = {
        //Breakpoint!
        println()
    }
}

fun bar(body: Context.() -> Unit) {
    with(Context(1)) {
        body()
    }
}

fun main() {
    bar(Account.foo())
}

// EXPRESSION: this
// RESULT: instance of Context(id=ID): LContext;

// EXPRESSION: this@Account
// RESULT: instance of Account(id=ID): LAccount;

// EXPRESSION: context
// RESULT: 1: I
