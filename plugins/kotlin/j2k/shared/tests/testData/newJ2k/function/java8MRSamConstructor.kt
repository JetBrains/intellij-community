// !ADD_JAVA_API
package test

import javaApi.JFunction0
import javaApi.JFunction1
import javaApi.JFunction2
import javaApi.MethodReferenceHelperClass
import test.Java8Class
import test.Test

internal class Test {
    fun memberFun(): Int {
        return 1
    }

    constructor(i: Int) : super()

    constructor()

    companion object {
        var field: Java8Class = Java8Class()
        fun staticFun(): Java8Class {
            return Java8Class()
        }

        fun testOverloads(): String {
            return "1"
        }

        fun testOverloads(i: Int): String {
            return "2"
        }
    }
}

internal class Test2

internal class Java8Class {
    private val field = Java8Class()
    private val h = MethodReferenceHelperClass()

    fun testStaticFunction() {
        val staticFunFromSameClass = JFunction0 { staticFun() }
        staticFunFromSameClass.foo()
        MethodReferenceHelperClass.staticFun0(JFunction0 { staticFun() })
        h.memberFun0(JFunction0 { staticFun() })

        val staticFunFromAnotherClass = JFunction0 { Test.Companion.staticFun() }
        staticFunFromAnotherClass.foo()
        MethodReferenceHelperClass.staticFun0(JFunction0 { Test.Companion.staticFun() })
        h.memberFun0(JFunction0 { Test.Companion.staticFun() })
    }

    fun testMemberFunctionThroughClass() {
        val memberFunFromClass = JFunction2 { obj: Java8Class? -> obj!!.memberFun() }
        memberFunFromClass.foo(Java8Class())
        MethodReferenceHelperClass.staticFun2<Java8Class?, Int?>(JFunction2 { obj: Java8Class? -> obj!!.memberFun() })
        h.memberFun2<Java8Class?, Int?>(JFunction2 { obj: Java8Class? -> obj!!.memberFun() })
    }

    fun testMemberFunctionThroughObject() {
        val obj = Java8Class()
        val memberFunFromSameClass = JFunction0 { obj.memberFun() }
        memberFunFromSameClass.foo()
        MethodReferenceHelperClass.staticFun0(JFunction0 { obj.memberFun() })
        h.memberFun0(JFunction0 { obj.memberFun() })

        val anotherObj = Test()
        val memFunFromAnotherClass = JFunction0 { anotherObj.memberFun() }
        memFunFromAnotherClass.foo()
        MethodReferenceHelperClass.staticFun0(JFunction0 { anotherObj.memberFun() })
        h.memberFun0(JFunction0 { anotherObj.memberFun() })

        val memberFunThroughObj1 = JFunction0 { field.memberFun() }
        memberFunThroughObj1.foo()
        MethodReferenceHelperClass.staticFun0(JFunction0 { field.memberFun() })
        h.memberFun0(JFunction0 { field.memberFun() })

        val memberFunThroughObj2 = JFunction0 { Test.Companion.field.memberFun() }
        memberFunThroughObj2.foo()
        MethodReferenceHelperClass.staticFun0(JFunction0 { Test.Companion.field.memberFun() })
        h.memberFun0(JFunction0 { Test.Companion.field.memberFun() })

        val memberFunThroughObj3 = JFunction0 { Test.Companion.staticFun().memberFun() }
        memberFunThroughObj3.foo()
        MethodReferenceHelperClass.staticFun0(JFunction0 { Test.Companion.staticFun().memberFun() })
        h.memberFun0(JFunction0 { Test.Companion.staticFun().memberFun() })
    }

    fun testConstructor() {
        val constructorSameClass = JFunction0 { Java8Class() }
        constructorSameClass.foo()
        MethodReferenceHelperClass.staticFun0(JFunction0 { Java8Class() })
        h.memberFun0(JFunction0 { Java8Class() })

        val qualifiedConstructorSameClass = JFunction0 { test.Java8Class() }
        qualifiedConstructorSameClass.foo()
        MethodReferenceHelperClass.staticFun0(JFunction0 { test.Java8Class() })
        h.memberFun0(JFunction0 { test.Java8Class() })

        val constructorAnotherClass = JFunction0 { Test() }
        constructorAnotherClass.foo()
        MethodReferenceHelperClass.staticFun0(JFunction0 { Test() })
        h.memberFun0(JFunction0 { Test() })

        val constructorAnotherClassWithParam = JFunction2 { i: Int? -> Test(i!!) }
        constructorAnotherClassWithParam.foo(1)
        MethodReferenceHelperClass.staticFun2<Int?, Test?>(JFunction2 { i: Int? -> Test(i!!) })
        h.memberFun2<Int?, Test?>(JFunction2 { i: Int? -> Test(i!!) })

        val qualifiedConstructorAnotherClass = JFunction0 { test.Test() }
        qualifiedConstructorAnotherClass.foo()
        MethodReferenceHelperClass.staticFun0(JFunction0 { test.Test() })
        h.memberFun0(JFunction0 { test.Test() })

        val constructorAnotherClassWithoutConstructor = JFunction0 { Test2() }
        constructorAnotherClassWithoutConstructor.foo()
        MethodReferenceHelperClass.staticFun0(JFunction0 { Test2() })
        h.memberFun0(JFunction0 { Test2() })
    }

    fun testLibraryFunctions() {
        val memberFunFromClass = JFunction2 { obj: String? -> obj!!.length }
        memberFunFromClass.foo("str")

        Thread(Runnable { println() }).start()
        java.lang.Runnable { kotlin.io.println() }.run()
    }

    fun testOverloads() {
        val constructorWithoutParams: JFunction1<String?> = JFunction1 { Test.Companion.testOverloads() }
        constructorWithoutParams.foo()
        MethodReferenceHelperClass.staticFun1<String?>(JFunction1 { Test.Companion.testOverloads() })
        h.memberFun1<String?>(JFunction1 { Test.Companion.testOverloads() })

        val constructorWithParam: JFunction2<Int?, String?> =
            JFunction2 { i: Int? -> Test.Companion.testOverloads(i!!) }
        constructorWithParam.foo(2)
        MethodReferenceHelperClass.staticFun2<Int?, String?>(JFunction2 { i: Int? -> Test.Companion.testOverloads(i!!) })
        h.memberFun2<Int?, String?>(JFunction2 { i: Int? -> Test.Companion.testOverloads(i!!) })
    }

    fun testGenericFunctions() {
        val emptyList: JFunction1<MutableList<String?>?> = JFunction1 { mutableListOf() }
        emptyList.foo()
        MethodReferenceHelperClass.staticFun1<MutableList<String?>?>(JFunction1 { mutableListOf() })
        h.memberFun1<MutableList<String?>?>(JFunction1 { mutableListOf() })
    }

    fun memberFun(): Int {
        return 1
    }

    companion object {
        fun staticFun(): Int {
            return 1
        }
    }
}
