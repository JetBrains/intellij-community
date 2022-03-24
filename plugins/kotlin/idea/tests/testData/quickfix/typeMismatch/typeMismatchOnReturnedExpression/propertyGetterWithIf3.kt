// "Change type of 'foo' to 'Any'" "true"
class O
class P

val foo: O
    get() {
        if (true) return O() else return P()<caret>
    }
