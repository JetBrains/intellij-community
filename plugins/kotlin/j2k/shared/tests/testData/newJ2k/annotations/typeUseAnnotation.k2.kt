// ERROR: This annotation is not applicable to target 'type parameter'.
// ERROR: This annotation is not applicable to target 'type parameter'.
import javaApi.Anon5
import javaApi.TypeUseAnon1
import javaApi.TypeUseAnon2
import javaApi.TypeUseAnon3
import java.io.File

class TEST1 {
    @Anon5(1)
    fun foo(@Anon5(2) o: @TypeUseAnon1 Any?): @TypeUseAnon1 String {
        @Anon5(3) val baz: @TypeUseAnon1 String = ""
        return ""
    }

    @Anon5(4)
    var bar: @TypeUseAnon1 String? = null
}

class TEST2 {
    @Anon5(1)
    fun foo(@Anon5(2) o: @TypeUseAnon2 Any?): @TypeUseAnon2 String {
        @Anon5(3) val baz: @TypeUseAnon2 String = ""
        return ""
    }

    @Anon5(4)
    var bar: @TypeUseAnon2 String? = null
}

class TEST3 {
    @Anon5(1)
    fun foo(@Anon5(2) o: @TypeUseAnon3 Any?): @TypeUseAnon3 String {
        @Anon5(3) val baz: @TypeUseAnon3 String = ""
        return ""
    }

    @Anon5(4)
    var bar: @TypeUseAnon3 String? = null
}

class TestInstanceOf {
    fun test(s: String?) {
        println(s is @TypeUseAnon3 String)
    }
}

class TestTypeCast {
    fun test(foo: Any?) {
        val s = foo as @TypeUseAnon3 String?
    }
}

class TestInheritance1 : @TypeUseAnon1 C()
class TestInheritance2 : @TypeUseAnon1 I1
class TestInheritance3 : @TypeUseAnon1 C(), @TypeUseAnon2 I1, @TypeUseAnon2 @TypeUseAnon3 I2

open class C
interface I1
interface I2

class TestCatch {
    fun foo() {
        try {
        } catch (e: @TypeUseAnon1 Exception) {
        }
    }
}

class TestForLoopParameter {
    fun foo(arr: IntArray) {
        for (test: @TypeUseAnon1 Int in arr) {
            println(test)
        }
        for (i: @TypeUseAnon1 Int in arr.indices) {
            println(i)
        }
    }
}

class TestPrimaryConstructorProperty(private var foo: @TypeUseAnon1 @TypeUseAnon2 String?)

class TestStandardMethods : Cloneable {
    override fun toString(): @TypeUseAnon1 String {
        return ""
    }

    @Throws(CloneNotSupportedException::class)
    public override fun clone(): @TypeUseAnon1 Any {
        return super.clone()
    }
}

annotation class TestAnnotationMethod(
    val value: @TypeUseAnon1 String,
    val value2: @TypeUseAnon1 @TypeUseAnon2 String = "test"
)

/**
 * TYPE_USE annotation is allowed on a type parameter only in Java,
 * in Kotlin an error is expected.
 */
interface TestTypeParameter<@TypeUseAnon1 F : @TypeUseAnon1 File?> {
    fun <@TypeUseAnon1 T : @TypeUseAnon1 File?> foo()
}

class TestTypeArgument {
    fun f1() {
        this.f2<@TypeUseAnon1 String?>("")
    }

    fun <T> f2(t: T?) {
    }
}
