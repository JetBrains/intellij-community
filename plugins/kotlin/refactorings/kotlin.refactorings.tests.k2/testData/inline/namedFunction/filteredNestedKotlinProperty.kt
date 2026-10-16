// CLASS_IMPORT_FILTER_VETO_REGEX: R.string
// CLASS_IMPORT_FILTER_ASSERT_VETO
// CLASS_IMPORT_FILTER_EXPECTED_IS_NESTED: true
// CLASS_IMPORT_FILTER_EXPECTED_CLASS_KIND: OBJECT

package test

import dependency.R

fun myGreatFunction() {
    val id = myOtherGreatFunction()
}

fun <caret>myOtherGreatFunction() = R.string.foo
