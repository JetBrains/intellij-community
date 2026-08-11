package sample

import sample.Source.Companion.foo

class Target

class Source {
    companion object {
        fun Source.foo<caret>(t: Target) {
            println(this)
        }
    }
}

fun Source.usage() {
    foo(Target())
}
