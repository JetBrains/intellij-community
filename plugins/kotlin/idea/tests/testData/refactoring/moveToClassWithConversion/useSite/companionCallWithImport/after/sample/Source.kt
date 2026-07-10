package sample

class Target {
    fun foo(source: Source) {
        println(source)
    }
}

class Source {
}

fun Source.usage() {
    Target().foo(this)
}

