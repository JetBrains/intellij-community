// ERROR: NOTHING_TO_OVERRIDE
// ERROR: NOTHING_TO_OVERRIDE
// ERROR: VIRTUAL_MEMBER_HIDDEN
// ERROR: VIRTUAL_MEMBER_HIDDEN
// ERROR: VIRTUAL_MEMBER_HIDDEN
// ERROR: VIRTUAL_MEMBER_HIDDEN
import javaApi.JavaClassDerivedFromKotlinClassWithProperties
import javaApi.JavaClassWithProperties
import kotlinApi.KotlinClassWithProperties

internal open class A : KotlinClassWithProperties() {
    var someVar1: String
        get() = super.someVar1
        set(s) {
            super.someVar1 = s
        }

    val someVar2: String
        get() = super.someVar2

    override fun setSomeVar3(s: String) {
        super.someVar3 = s
    }

    val someVar4: String
        get() = super.someVar4

    val someVal: String
        get() = super.someVal

    override fun getSomething1() {
        super.getSomething1()
    }

    override fun getSomething2() {
        super.getSomething2()
    }

    override fun setSomething2(value: Int) {
        super.setSomething2(value)
    }

    override fun getSomething3() {
        super.getSomething3()
    }

    override fun setSomething4(value: Int) {
        super.setSomething4(value)
    }
}

internal class B : JavaClassWithProperties() {
    override fun getValue1(): Int {
        return super.getValue1()
    }

    override fun getValue2(): Int {
        return super.getValue2()
    }

    override fun setValue2(value: Int) {
        super.setValue2(value)
    }

    override fun getValue3(): Int {
        return super.getValue3()
    }

    override fun setValue4(value: Int) {
        super.setValue4(value)
    }
}

internal class C : A() {
    override fun getSomeVar1(): String {
        return super.someVar1
    }
}

internal class D : JavaClassDerivedFromKotlinClassWithProperties() {
    override fun getSomeVar1(): String {
        return "a"
    }

    override fun setSomeVar2(value: String?) {}
}
