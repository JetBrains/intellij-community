package superWithArguments

class Help(val string: String)

fun getHelp(string: String): Help {
    return Help(string)
}

open class Base {
    open fun foo(x: String, y: Int = 81, help: Help = Help("Help...")): String {
        return "Hello $x, ${y}th time for today. ${help.string}"
    }
}

class JustTestClass : Base() {
    override fun foo(x: String, y: Int, help: Help): String {
        //Breakpoint!
        return super.foo(x, y, help) + " me!"
    }
}

fun main() {
    val help = Help("Help!")
    println("R = ${JustTestClass().foo("IR", 42, help)}")
}

// EXPRESSION: super.foo("IR", 42, help)
// RESULT: "Hello IR, 42th time for today. Help!": Ljava/lang/String;

// EXPRESSION: super.foo(listOf(1, 2, 3).joinToString(), "Hello there!".length, getHelp("Help!"))
// RESULT: "Hello 1, 2, 3, 12th time for today. Help!": Ljava/lang/String;

// EXPRESSION: super.foo("folks", help = getHelp("help..."), y = 51)
// RESULT: "Hello folks, 51th time for today. help...": Ljava/lang/String;