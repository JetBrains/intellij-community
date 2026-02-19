// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test.unit

import com.intellij.debugger.mockJDI.MockVirtualMachine
import org.jetbrains.kotlin.idea.debugger.base.util.isSubtype
import org.jetbrains.org.objectweb.asm.Type
import org.junit.Test
import kotlin.test.assertTrue

internal class JdiTypeUtilsTest {
    @Test
    fun testIsSubtypeInterfaceExtendsInterface() {
        val mockVirtualMachine = MockVirtualMachine()
        val interfaceType = mockVirtualMachine.createInterfaceType(List::class.java)
        val expectedAsmType = Type.getType(Collection::class.java)
        assertTrue(interfaceType.isSubtype(expectedAsmType))
    }
}
