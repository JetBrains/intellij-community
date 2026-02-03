package bar

import foo.*

class CrExtended

fun <caret>test(ce: CrExtended) {
    1::extValWithFunType
    1::extValWithExtFunType
}