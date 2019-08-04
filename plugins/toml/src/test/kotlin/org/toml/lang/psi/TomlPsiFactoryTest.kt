/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

// BACKCOMPAT: 2019.1
@file:Suppress("DEPRECATION")

package org.toml.lang.psi

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase

// BACKCOMPAT: 2019.1. Use BasePlatformTestCase instead
class TomlPsiFactoryTest : LightPlatformCodeInsightFixtureTestCase() {
    private val factory: TomlPsiFactory get() = TomlPsiFactory(project)

    fun `test create value`() {
        val value = factory.createValue("\"value\"")
        assertEquals("\"value\"", value.text)
    }

    fun `test create key`() {
        val key = factory.createKey("key")
        assertEquals("key", key.text)
    }

    fun `test create key value 1`() {
        val keyValue = factory.createKeyValue("key")
        assertEquals("key", keyValue.key.text)
    }

    fun `test create key value 2`() {
        val keyValue = factory.createKeyValue("key", "\"value\"")
        assertEquals("key", keyValue.key.text)
        assertEquals("\"value\"", keyValue.value!!.text)
    }

    fun `test create table`() {
        val table = factory.createTable("key1.key2")
        assertEquals("[key1.key2]", table.text)
    }

    fun `test create table header`() {
        val tableHeader = factory.createTableHeader("key1.key2")
        assertEquals("[key1.key2]", tableHeader.text)
        assertEquals(listOf("key1", "key2"), tableHeader.names.map { it.text })
    }

    fun `test create array`() {
        val array = factory.createArray("1, [2], [[3]]")
        assertEquals("[1, [2], [[3]]]", array.text)
        assertEquals(listOf("1", "[2]", "[[3]]"), array.elements.map { it.text })
    }

    fun `test create inline table`() {
        val inlineTable = factory.createInlineTable("key = \"value\"")
        assertEquals("{key = \"value\"}", inlineTable.text)
    }
}
