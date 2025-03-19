package a

class InvokeObject

operator fun InvokeObject.invoke(p: Int) { }

fun use<caret>Invoke(p: InvokeObject) {
    p(0)
}