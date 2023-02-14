open class B: A() {
    override fun a() = Unit
}

open class B2: A() {
    override fun a() = Unit
}

class B3: A() {
    final override fun a() = Unit
}

class C : B() {
    override fun a() = Unit
}

class C2 : B() {
    override fun a() = Unit
}

interface KotlinInterface {
    fun a()
}

open class B3: A(), KotlinInterface {
    override fun a() = Unit
}

interface NextKotlinInterface : KotlinInterface

open class B4: A(), NextKotlinInterface {
    override fun a() = Unit
}

abstract class B5 : A() {
    abstract override fun a()
}

class C3 : B5() {
    override fun a() = Unit
}

class C4 : B5() {
    final override fun a() = Unit
}

class B6 : A() {
    final fun a() = Unit
}
