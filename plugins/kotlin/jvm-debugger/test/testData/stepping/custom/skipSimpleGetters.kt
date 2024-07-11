fun foo(name: String, age: Int): Any {
    return name
}

class User1(
    val name: String,
    val age: Int
)

private fun test1() {
    val user = User1("", 0)
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!
    foo(user.name, user.age)
}

class User2(val name: String, val age: Int)

private fun test2() {
    val user = User2("", 0)
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!
    foo(user.name, user.age)
}

fun main() {
    test1()
    test2()
}

// SKIP_GETTERS: true
