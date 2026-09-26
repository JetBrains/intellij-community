/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi

import org.toml.TomlTestBase

class TomlPsiFactoryTest : TomlTestBase() {
    private val factory: TomlPsiFactory get() = TomlPsiFactory(project)

    fun `test create literal`() {
        val literal = factory.createLiteral("\"value\"")
        assertEquals("\"value\"", literal.text)
    }

    fun `test create key segment`() {
        val keySegment = factory.createKeySegment("segment")
        assertEquals("segment", keySegment.name)
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
        assertEquals(listOf("key1", "key2"), tableHeader.key?.segments.orEmpty().map { it.text })
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
