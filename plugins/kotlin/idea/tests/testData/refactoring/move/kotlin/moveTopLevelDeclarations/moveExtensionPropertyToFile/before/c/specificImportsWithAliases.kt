package c

import a.test as _test
import a.Test as _Test

fun bar() {
    _Test()._test
    _Test()._test = 0
}
