// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test.unit

import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.toTypedList
import org.junit.Test
import org.junit.jupiter.api.assertThrows

internal class ToTypedListTest {
    @Test
    fun testKotlinLists() {
        listOf(1, 2, 3).toTypedList<Int>()
        listOf(1, 2, 3).toTypedList<Int?>()
        listOf(1, null, 3).toTypedList<Int?>()
        assertThrows<IllegalArgumentException> {
            listOf(1, null, 3).toTypedList<Int>()
        }
        assertThrows<IllegalArgumentException> {
            listOf(1.0, 2.0, 3).toTypedList<Int>()
        }
    }

    @Test
    fun testJavaLists() {
        JavaListWrapper(listOf(1, 2, 3)).numbers.toTypedList<Int>()
        JavaListWrapper(listOf(1, 2, 3)).numbers.toTypedList<Int?>()
        JavaListWrapper(listOf(1, null, 3)).numbers.toTypedList<Int?>()
        assertThrows<IllegalArgumentException> {
            JavaListWrapper(listOf(1, null, 3)).numbers.toTypedList<Int>()
        }
        assertThrows<IllegalArgumentException> {
            JavaListWrapper(listOf(1.0, 2.0, 3)).numbers.toTypedList<Int>()
        }
    }
}
