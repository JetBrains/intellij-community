/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi

import com.intellij.psi.util.parentOfType
import org.intellij.lang.annotations.Language
import org.toml.TomlTestBase
import kotlin.reflect.KClass

class TomlVisitorTest : TomlTestBase() {

    fun `test visit key segment`() = doTest<TomlKeySegment>("""
        <caret>a = 5
    """)

    fun `test visit key`() = doTest<TomlKey>("""
        <caret>a = 5
    """)

    fun `test visit literal`() = doTest<TomlLiteral>("""
        a = <caret>5
    """, TomlValue::class)

    fun `test visit key value`() = doTest<TomlKeyValue>("""
        <caret>a = 5
    """)

    fun `test visit array`() = doTest<TomlArray>("""
        a = <caret>[5]
    """, TomlValue::class)

    fun `test visit table`() = doTest<TomlTable>("""
        <caret>[foo]
    """, TomlKeyValueOwner::class)

    fun `test visit table header`() = doTest<TomlTableHeader>("""
        [<caret>foo]
    """)

    fun `test visit inline table`() = doTest<TomlInlineTable>("""
        a = { b = <caret>5 }
    """, TomlKeyValueOwner::class)

    fun `test visit array table`() = doTest<TomlArrayTable>("""
        [[<caret>foo]]
    """, TomlKeyValueOwner::class)

    private inline fun <reified T: TomlElement> doTest(
        @Language("TOML") code: String,
        vararg additionalVisits: KClass<out TomlElement>
    ) {
        InlineFile(code)
        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<T>(true)!!

        val visits = mutableListOf<KClass<out TomlElement>>()
        val visitor = object : TomlVisitor() {
            override fun visitElement(element: TomlElement) {
                visits.add(TomlElement::class)
                super.visitElement(element)
            }

            override fun visitValue(element: TomlValue) {
                visits.add(TomlValue::class)
                super.visitValue(element)
            }

            override fun visitKeyValue(element: TomlKeyValue) {
                visits.add(TomlKeyValue::class)
                super.visitKeyValue(element)
            }

            override fun visitKeySegment(element: TomlKeySegment) {
                visits.add(TomlKeySegment::class)
                super.visitKeySegment(element)
            }

            override fun visitKey(element: TomlKey) {
                visits.add(TomlKey::class)
                super.visitKey(element)
            }

            override fun visitLiteral(element: TomlLiteral) {
                visits.add(TomlLiteral::class)
                super.visitLiteral(element)
            }

            override fun visitKeyValueOwner(element: TomlKeyValueOwner) {
                visits.add(TomlKeyValueOwner::class)
                super.visitKeyValueOwner(element)
            }

            override fun visitArray(element: TomlArray) {
                visits.add(TomlArray::class)
                super.visitArray(element)
            }

            override fun visitTable(element: TomlTable) {
                visits.add(TomlTable::class)
                super.visitTable(element)
            }

            override fun visitTableHeader(element: TomlTableHeader) {
                visits.add(TomlTableHeader::class)
                super.visitTableHeader(element)
            }

            override fun visitInlineTable(element: TomlInlineTable) {
                visits.add(TomlInlineTable::class)
                super.visitInlineTable(element)
            }

            override fun visitArrayTable(element: TomlArrayTable) {
                visits.add(TomlArrayTable::class)
                super.visitArrayTable(element)
            }
        }
        element.accept(visitor)
        assertEquals(listOf(T::class, *additionalVisits, TomlElement::class), visits)
    }
}
