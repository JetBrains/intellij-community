class Type1
class Type2
class Type3
class Type4
class Type5

context(context1: Type1, context2: Type2)
fun Type3.topLevel(param1: Type4, param2: Type5) {}

class Cls {
    context(context1: Type1, context2: Type2)
    fun Type3.member(param1: Type4, param2: Type5) {}

    companion object {
        context(context1: Type1, context2: Type2)
        fun Type3.companionObject(param1: Type4, param2: Type5) {}
    }
}

object Obj {
    context(context1: Type1, context2: Type2)
    fun Type3.topLevelObject(param1: Type4, param2: Type5) {}
}
