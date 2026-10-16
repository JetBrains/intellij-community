// CLASS_IMPORT_FILTER_VETO_REGEX: R.string

package test

fun myGreatFunction() {
    val id = myOtherGreatFunction()
}

fun <caret>myOtherGreatFunction() = R.string.foo
