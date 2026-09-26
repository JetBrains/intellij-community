// IDEA-330748
package smartStepIntoAnonymousObjectReceiver

fun main() {
    val norm = Object()
    //Breakpoint!
    val anon = object : Object() {}
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OUT: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // STEP_OVER: 1
    val res = norm.toString() + anon.toString() + anon.foo(-42)
    println(res)
}

fun Object.foo(n: Int): Int {
    return this.hashCode() + n
}
