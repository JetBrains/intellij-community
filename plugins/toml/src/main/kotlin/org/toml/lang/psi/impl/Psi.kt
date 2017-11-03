/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.tree.TokenSet
import org.toml.lang.psi.*
import org.toml.lang.psi.TomlElementTypes.*


class TomlKeyValueImpl(node: ASTNode) : ASTWrapperPsiElement(node), TomlKeyValue {
    override val key: TomlKey
        get() = notNullChild(findChildByType(KEY))

    override val value: TomlValue?
        get() = findChildByType(KEY)

    override fun toString(): String = "TomlKeyValue"
}


class TomlKeyImpl(node: ASTNode) : ASTWrapperPsiElement(node), TomlKey {
    override fun getReferences(): Array<PsiReference>
        = ReferenceProvidersRegistry.getReferencesFromProviders(this)

    override fun toString(): String = "TomlKey"
}

class TomlLiteralImpl(node: ASTNode) : ASTWrapperPsiElement(node), TomlLiteral {
    override fun getReferences(): Array<PsiReference>
        = ReferenceProvidersRegistry.getReferencesFromProviders(this)

    override fun toString(): String = "TomlLiteral"
}

class TomlArrayImpl(node: ASTNode) : ASTWrapperPsiElement(node), TomlArray {
    override val elements: List<TomlValue>
        get() = findChildrenByType(VALUES)

    override fun toString(): String = "TomlArray"
}

class TomlTableImpl(node: ASTNode) : ASTWrapperPsiElement(node), TomlTable {
    override val header: TomlTableHeader
        get() = notNullChild(findChildByType(TABLE_HEADER))

    override val entries: List<TomlKeyValue>
        get() = findChildrenByType(KEY_VALUE)

    override fun toString(): String = "TomlTable"
}

class TomlTableHeaderImpl(node: ASTNode) : ASTWrapperPsiElement(node), TomlTableHeader {
    override val names: List<TomlKey>
        get() = findChildrenByType(KEY)

    override fun toString(): String = "TomlTableHeader"
}

class TomlInlineTableImpl(node: ASTNode) : ASTWrapperPsiElement(node), TomlInlineTable {
    override val entries: List<TomlKeyValue>
        get() = findChildrenByType(KEY_VALUE)

    override fun toString(): String = "TomlInlineTable"
}

class TomlArrayTableImpl(node: ASTNode) : ASTWrapperPsiElement(node), TomlArrayTable {
    override val header: TomlTableHeader
        get() = notNullChild(findChildByType(TABLE_HEADER))

    override val entries: List<TomlKeyValue>
        get() = findChildrenByType(KEY_VALUE)

    override fun toString(): String = "TomlArrayTable"
}

fun createPsiElement(node: ASTNode) = when (node.elementType) {
    KEY_VALUE -> TomlKeyValueImpl(node)
    KEY -> TomlKeyImpl(node)
    LITERAL -> TomlLiteralImpl(node)
    ARRAY -> TomlArrayImpl(node)
    TABLE -> TomlTableImpl(node)
    TABLE_HEADER -> TomlTableHeaderImpl(node)
    INLINE_TABLE -> TomlInlineTableImpl(node)
    ARRAY_TABLE -> TomlArrayTableImpl(node)
    else -> error("Unknown TOML element type: `${node.elementType}`")
}

private val VALUES = TokenSet.create(
    LITERAL, ARRAY, INLINE_TABLE
)
