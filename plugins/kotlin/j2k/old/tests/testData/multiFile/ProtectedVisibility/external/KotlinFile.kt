package test3

import test.*

class DerivedOtherPackageKotlin : BaseOtherPackage() {
    init {
        foo()
        val i = this.i
    }
}