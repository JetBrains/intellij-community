class C1
class C2
class C3

fun outer(){
    context(c1: C1)
    fun C2.local(c3: C3) {}
}
