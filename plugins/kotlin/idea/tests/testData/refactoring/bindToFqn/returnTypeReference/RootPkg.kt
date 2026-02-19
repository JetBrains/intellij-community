// BIND_TO B
interface A { }

class B : A { }

fun foo(): <caret>A = B()