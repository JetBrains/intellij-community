package b

import a.ProtectedContainer

open class ProtectedReference : ProtectedContainer() {
    fun refer() = protectedMethod()
    protected inner class InnerClass : ProtectedInnerClass()
    protected class StaticClass : ProtectedStaticClass()
}