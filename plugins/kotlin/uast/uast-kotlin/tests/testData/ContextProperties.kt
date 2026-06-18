class C1
class C2
class C3
class C4

context(c2: C2)
val topLevelProp: C1
    get() = C1()

context(c2: C2)
var topLevelMutableProp: C1
    get() = C1()
    set(value) {}

class Cls {
    context(c3: C3)
    val memberProp: C4
        get() = C4()

    context(c3: C3)
    var mutableMemberProp: C4
        get() = C4()
        set(value) {}
}
