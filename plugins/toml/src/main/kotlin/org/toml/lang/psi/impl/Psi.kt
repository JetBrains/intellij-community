/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi.impl

import com.intellij.lang.ASTFactory
import com.intellij.lang.psi.SimpleMultiLineTextEscaper
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.CompositePsiElement
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.toml.lang.psi.*
import org.toml.lang.psi.TomlElementTypes.*


class TomlKeyValueImpl(type: IElementType) : CompositePsiElement(type), TomlKeyValue {
    override val key: TomlKey get() = childOfTypeNotNull()
    override val value: TomlValue? get() = childOfTypeNullable()
    override fun toString(): String = "TomlKeyValue"
}

class TomlKeyImpl(type: IElementType) : CompositePsiElement(type), TomlKey {
    override fun getReferences(): Array<PsiReference>
        = ReferenceProvidersRegistry.getReferencesFromProviders(this)

    override fun toString(): String = "TomlKey"
}

class TomlLiteralImpl(type: IElementType) : CompositePsiElement(type), TomlLiteral {
    override fun getReferences(): Array<PsiReference>
        = ReferenceProvidersRegistry.getReferencesFromProviders(this)

    override fun toString(): String = "TomlLiteral"

    override fun isValidHost(): Boolean =
        node.findChildByType(TOML_STRING_LITERALS) != null

    override fun updateText(text: String): PsiLanguageInjectionHost {
        val valueNode = node.firstChildNode
        assert(valueNode is LeafElement)
        (valueNode as LeafElement).replaceWithText(text)
        return this
    }

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> {
        val tokenType = node.findChildByType(TOML_STRING_LITERALS)?.elementType ?: error("$text is not string literal")
        return if (tokenType in TOML_BASIC_STRINGS) TomlLiteralTextEscaper(this) else SimpleMultiLineTextEscaper(this)
    }
}

class TomlArrayImpl(type: IElementType) : CompositePsiElement(type), TomlArray {
    override val elements: List<TomlValue> get() = childrenOfType()
    override fun toString(): String = "TomlArray"
}

class TomlTableImpl(type: IElementType) : CompositePsiElement(type), TomlTable {
    override val header: TomlTableHeader get() = childOfTypeNotNull()
    override val entries: List<TomlKeyValue> get() = childrenOfType()
    override fun toString(): String = "TomlTable"
}

class TomlTableHeaderImpl(type: IElementType) : CompositePsiElement(type), TomlTableHeader {
    override val names: List<TomlKey> get() = childrenOfType()
    override fun toString(): String = "TomlTableHeader"
}

class TomlInlineTableImpl(type: IElementType) : CompositePsiElement(type), TomlInlineTable {
    override val entries: List<TomlKeyValue> get() = childrenOfType()

    override fun toString(): String = "TomlInlineTable"
}

class TomlArrayTableImpl(type: IElementType) : CompositePsiElement(type), TomlArrayTable {
    override val header: TomlTableHeader get() = childOfTypeNotNull()
    override val entries: List<TomlKeyValue> get() = childrenOfType()
    override fun toString(): String = "TomlArrayTable"
}

class TomlASTFactory : ASTFactory() {
    override fun createComposite(type: IElementType): CompositeElement? = when (type) {
        KEY_VALUE -> TomlKeyValueImpl(type)
        KEY -> TomlKeyImpl(type)
        LITERAL -> TomlLiteralImpl(type)
        ARRAY -> TomlArrayImpl(type)
        TABLE -> TomlTableImpl(type)
        TABLE_HEADER -> TomlTableHeaderImpl(type)
        INLINE_TABLE -> TomlInlineTableImpl(type)
        ARRAY_TABLE -> TomlArrayTableImpl(type)
        else -> error("Unknown TOML element type: `$type`")
    }
}

private inline fun <reified T : TomlElement> CompositePsiElement.childOfTypeNullable(): T? =
    PsiTreeUtil.getChildOfType(this, T::class.java)

private inline fun <reified T : TomlElement> CompositePsiElement.childOfTypeNotNull(): T =
    PsiTreeUtil.getChildOfType(this, T::class.java)
        ?: error("""
            Invalid TOML PSI
            Expected to find `${T::class.simpleName}` child of ${this::class.simpleName}
            Element text:
        """.trimIndent() + "\n$text"
    )

private inline fun <reified T : TomlElement> CompositePsiElement.childrenOfType(): List<T> =
    PsiTreeUtil.getChildrenOfTypeAsList(this, T::class.java)
