// LANGUAGE: +NestedTypeAliases
package sample

open class <caret>Foo {
    // INFO: {"checked": "true"}
    typealias Nested = String
}

class Bar : Foo() {

}

fun use(value: Foo.Nested): Foo.Nested = value
