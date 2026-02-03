// !ADD_JAVA_API
package test

import javaApi.MethodReferenceHelperClass

internal class Test {
    fun memberFun(): Int {
        TODO()
    }

    constructor(i: Int) {
        TODO()
    }

    constructor() {
        TODO()
    }

    companion object {
        var field: Java8Class = Java8Class()
        fun staticFun(): Java8Class {
            TODO()
        }

        fun testOverloads(): String {
            TODO()
        }

        fun testOverloads(i: Int): String {
            TODO()
        }
    }
}

internal class Test2

internal class Java8Class {
    private val field: Java8Class = TODO()
    private val h: MethodReferenceHelperClass = TODO()

    fun testStaticFunction() {
        TODO()
    }

    fun testMemberFunctionThroughClass() {
        TODO()
    }

    fun testMemberFunctionThroughObject() {
        TODO()
    }

    fun testConstructor() {
        TODO()
    }

    fun testLibraryFunctions() {
        TODO()
    }

    fun testOverloads() {
        TODO()
    }

    fun testGenericFunctions() {
        TODO()
    }

    fun memberFun(): Int {
        TODO()
    }

    init {
        TODO()
    }

    companion object {
        fun staticFun(): Int {
            TODO()
        }
    }
}
