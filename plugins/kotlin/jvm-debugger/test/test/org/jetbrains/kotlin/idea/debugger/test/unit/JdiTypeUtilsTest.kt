// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test.unit

import com.intellij.debugger.mockJDI.MockVirtualMachine
import org.jetbrains.kotlin.idea.debugger.base.util.findMethod
import org.jetbrains.kotlin.idea.debugger.base.util.hasInterface
import org.jetbrains.kotlin.idea.debugger.base.util.hasSuperClass
import org.jetbrains.kotlin.idea.debugger.base.util.isSubtype
import org.jetbrains.org.objectweb.asm.Type
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class JdiTypeUtilsTest {
    private val mockVirtualMachine = MockVirtualMachine()

    @Test
    fun testIsSubtypeInterfaceExtendsInterface() {
        val interfaceType = mockVirtualMachine.createInterfaceType(List::class.java)
        val expectedAsmType = Type.getType(Collection::class.java)
        assertTrue(interfaceType.isSubtype(expectedAsmType))
    }

    @Test
    fun testIsSubtypeExactMatch() {
        val classType = mockVirtualMachine.createReferenceType(String::class.java)
        val asmType = Type.getType(String::class.java)
        assertTrue(classType.isSubtype(asmType))
    }

    @Test
    fun testIsSubtypeClassExtendsClass() {
        val classType = mockVirtualMachine.createReferenceType(RuntimeException::class.java)
        val asmType = Type.getType(Exception::class.java)
        assertTrue(classType.isSubtype(asmType))
    }

    @Test
    fun testIsSubtypeNotSubtype() {
        val classType = mockVirtualMachine.createReferenceType(String::class.java)
        val asmType = Type.getType(Number::class.java)
        assertFalse(classType.isSubtype(asmType))
    }

    @Test
    fun testIsSubtypePrimitiveAsmType() {
        val classType = mockVirtualMachine.createReferenceType(String::class.java)
        val asmType = Type.INT_TYPE
        assertFalse(classType.isSubtype(asmType))
    }

    @Test
    fun testIsSubtypePrimitiveJdiType() {
        val booleanType = mockVirtualMachine.booleanType
        val asmType = Type.getType(Any::class.java)
        assertFalse(booleanType.isSubtype(asmType))
    }

    @Test
    fun testHasSuperClassDirect() {
        val classType = mockVirtualMachine.createReferenceType(RuntimeException::class.java) as com.sun.jdi.ClassType
        assertTrue(classType.hasSuperClass("java.lang.RuntimeException"))
    }

    @Test
    fun testHasSuperClassTransitive() {
        val classType = mockVirtualMachine.createReferenceType(RuntimeException::class.java) as com.sun.jdi.ClassType
        assertTrue(classType.hasSuperClass("java.lang.Exception"))
    }

    @Test
    fun testHasSuperClassNotFound() {
        val classType = mockVirtualMachine.createReferenceType(RuntimeException::class.java) as com.sun.jdi.ClassType
        assertFalse(classType.hasSuperClass("com.example.NonExistent"))
    }

    @Test
    fun testHasInterfaceTrue() {
        val classType = mockVirtualMachine.createReferenceType(java.util.ArrayList::class.java) as com.sun.jdi.ClassType
        assertTrue(classType.hasInterface("java.util.List"))
    }

    @Test
    fun testHasInterfaceFalse() {
        val classType = mockVirtualMachine.createReferenceType(java.util.ArrayList::class.java) as com.sun.jdi.ClassType
        assertFalse(classType.hasInterface("java.util.Map"))
    }

    @Test
    fun testFindMethodFound() {
        val classType = mockVirtualMachine.createReferenceType(String::class.java)
        val method = classType.findMethod("length", null)
        assertEquals("length", method.name())
    }

    @Test
    fun testFindMethodNotFound() {
        val classType = mockVirtualMachine.createReferenceType(String::class.java)
        assertFailsWith<IllegalStateException> {
            classType.findMethod("nonExistentMethod", null)
        }
    }
}
