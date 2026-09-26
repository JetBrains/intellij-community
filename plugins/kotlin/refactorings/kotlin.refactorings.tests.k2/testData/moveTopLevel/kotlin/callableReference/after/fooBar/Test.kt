package fooBar

import bar.CrExtended
import foo.extValWithExtFunType
import foo.extValWithFunType

fun test(ce: CrExtended) {
    1::extValWithFunType
    1::extValWithExtFunType
}