// WITH_STDLIB
interface T

abstract class <caret>B: T {
    // INFO: {"checked": "true"}
    inner class X {

    }
}