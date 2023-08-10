fun findClasses() {
    val classloader = B::class.java.classLoader
    val obj = <selection>classloader?.loadClass("hi")?.kotlin</selection>
}

class B
