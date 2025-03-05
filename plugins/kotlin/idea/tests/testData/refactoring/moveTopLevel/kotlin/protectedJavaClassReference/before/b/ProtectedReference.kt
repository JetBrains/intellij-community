package b

import a.ProtectedContainer

open class ProtectedReference<caret> : ProtectedContainer() {
    fun refer() = protectedMethod()
    protected inner class InnerClass : ProtectedInnerClass()
    protected class StaticClass : ProtectedStaticClass()
}