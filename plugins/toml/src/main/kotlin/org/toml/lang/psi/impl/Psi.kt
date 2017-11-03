/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import org.toml.lang.psi.TomlElementTypes.*
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlValue


class TomlKeyValueImpl(node: ASTNode) : ASTWrapperPsiElement(node), TomlKeyValue {
    override val key: TomlKey
        get() = notNullChild(findChildByType(KEY))

    override val value: TomlValue?
        get() = findChildByType(KEY)
}


class TomlKeyImpl(node: ASTNode) : ASTWrapperPsiElement(node), TomlKey
class TomlLiteralImpl(node: ASTNode) : ASTWrapperPsiElement(node), TomlValue

fun createPsiElement(node: ASTNode) = when (node.elementType) {
    KEY_VALUE -> TomlKeyValueImpl(node)
    KEY -> TomlKeyImpl(node)
    LITERAL -> TomlLiteralImpl(node)
    else -> TODO("No PSI for $node")
}
