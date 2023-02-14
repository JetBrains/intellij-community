/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi.impl

import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTFactory
import org.toml.lang.psi.SimpleMultiLineTextEscaper
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.CompositePsiElement
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.toml.lang.psi.*
import org.toml.lang.psi.TomlElementTypes.*
import org.toml.lang.psi.ext.TomlLiteralKind


class TomlKeyValueImpl(type: IElementType) : CompositePsiElement(type), TomlKeyValue {
    override val key: TomlKey get() = childOfTypeNotNull()
    override val value: TomlValue? get() = childOfTypeNullable()
    override fun toString(): String = "TomlKeyValue"

    override fun accept(visitor: PsiElementVisitor) {
        if (visitor is TomlVisitor) {
            visitor.visitKeyValue(this)
        } else {
            super.accept(visitor)
        }
    }
}

class TomlKeySegmentImpl(type: IElementType) : CompositePsiElement(type), TomlKeySegment {

    override fun getName(): String = when (val child = node.findChildByType(TOML_SINGLE_LINE_STRINGS)) {
        null -> text
        else -> (TomlLiteralKind.fromAstNode(child) as? TomlLiteralKind.String)?.value ?: text
    }

    override fun setName(name: String): PsiElement {
        return replace(TomlPsiFactory(project).createKeySegment(name))
    }

    override fun getPresentation(): ItemPresentation = PresentationData(name, null, null, null)

    override fun toString(): String = "TomlKeySegment"
    override fun getReferences(): Array<PsiReference> = ReferenceProvidersRegistry.getReferencesFromProviders(this)

    override fun accept(visitor: PsiElementVisitor) {
        if (visitor is TomlVisitor) {
            visitor.visitKeySegment(this)
        } else {
            super.accept(visitor)
        }
    }
}

class TomlKeyImpl(type: IElementType) : CompositePsiElement(type), TomlKey {
    override val segments: List<TomlKeySegment> get() = childrenOfType()

    override fun toString(): String = "TomlKey"

    override fun accept(visitor: PsiElementVisitor) {
        if (visitor is TomlVisitor) {
            visitor.visitKey(this)
        } else {
            super.accept(visitor)
        }
    }
}

class TomlLiteralImpl(type: IElementType) : CompositePsiElement(type), TomlLiteral {
    override fun getReferences(): Array<PsiReference> = ReferenceProvidersRegistry.getReferencesFromProviders(this)

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

    override fun accept(visitor: PsiElementVisitor) {
        if (visitor is TomlVisitor) {
            visitor.visitLiteral(this)
        } else {
            super.accept(visitor)
        }
    }
}

class TomlArrayImpl(type: IElementType) : CompositePsiElement(type), TomlArray {
    override val elements: List<TomlValue> get() = childrenOfType()
    override fun toString(): String = "TomlArray"

    override fun accept(visitor: PsiElementVisitor) {
        if (visitor is TomlVisitor) {
            visitor.visitArray(this)
        } else {
            super.accept(visitor)
        }
    }
}

class TomlTableImpl(type: IElementType) : CompositePsiElement(type), TomlTable {
    override val header: TomlTableHeader get() = childOfTypeNotNull()
    override val entries: List<TomlKeyValue> get() = childrenOfType()
    override fun toString(): String = "TomlTable"

    override fun accept(visitor: PsiElementVisitor) {
        if (visitor is TomlVisitor) {
            visitor.visitTable(this)
        } else {
            super.accept(visitor)
        }
    }
}

class TomlTableHeaderImpl(type: IElementType) : CompositePsiElement(type), TomlTableHeader {
    override val key: TomlKey? get() = childOfTypeNullable()
    override fun toString(): String = "TomlTableHeader"

    override fun accept(visitor: PsiElementVisitor) {
        if (visitor is TomlVisitor) {
            visitor.visitTableHeader(this)
        } else {
            super.accept(visitor)
        }
    }
}

class TomlInlineTableImpl(type: IElementType) : CompositePsiElement(type), TomlInlineTable {
    override val entries: List<TomlKeyValue> get() = childrenOfType()

    override fun toString(): String = "TomlInlineTable"

    override fun accept(visitor: PsiElementVisitor) {
        if (visitor is TomlVisitor) {
            visitor.visitInlineTable(this)
        } else {
            super.accept(visitor)
        }
    }
}

class TomlArrayTableImpl(type: IElementType) : CompositePsiElement(type), TomlArrayTable {
    override val header: TomlTableHeader get() = childOfTypeNotNull()
    override val entries: List<TomlKeyValue> get() = childrenOfType()
    override fun toString(): String = "TomlArrayTable"

    override fun accept(visitor: PsiElementVisitor) {
        if (visitor is TomlVisitor) {
            visitor.visitArrayTable(this)
        } else {
            super.accept(visitor)
        }
    }
}

class TomlASTFactory : ASTFactory() {
    override fun createComposite(type: IElementType): CompositeElement = when (type) {
        KEY_VALUE -> TomlKeyValueImpl(type)
        KEY_SEGMENT -> TomlKeySegmentImpl(type)
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
