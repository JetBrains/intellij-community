// IMPORT_NESTED_CLASSES: true
package test

class TopLevel {
    object NestedObj {
        fun funFromObj() {}
    }
}

fun usage() {
    <selection>test.TopLevel.NestedObj.funFromObj()</selection>
}