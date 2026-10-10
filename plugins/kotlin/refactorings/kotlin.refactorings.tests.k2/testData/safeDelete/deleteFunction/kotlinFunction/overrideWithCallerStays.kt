// SAFE_DELETE_OPTIONS: kotlinDeleteSuperDeclarations=true
// As in the legacy flow, the base function goes instead of the override, and an override with a caller stays without `override`.
package test

open class Base {
    open fun f() {}
}

class Impl : Base() {
    override fun <caret>f() {}
}

fun use(impl: Impl) = impl.f()