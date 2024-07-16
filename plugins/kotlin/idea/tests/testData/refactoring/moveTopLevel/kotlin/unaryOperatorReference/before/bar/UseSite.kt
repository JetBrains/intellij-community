package bar

import foo.doFoo

fun foo<caret>Bar() {
    if (!doFoo()) return
}