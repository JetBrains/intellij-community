/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi

import com.intellij.psi.PsiElement

interface TomlElement : PsiElement

// region abstract base types

interface TomlValue : TomlElement

interface TomlKeyValueOwner : TomlElement {
    val entries: List<TomlKeyValue>
}

interface TomlHeaderOwner: TomlElement {
    val header: TomlTableHeader
}

// endregion

interface TomlKeyValue : TomlElement {
    val key: TomlKey
    val value: TomlValue?
}

interface TomlKey : TomlElement


interface TomlLiteral : TomlValue

interface TomlArray : TomlValue {
    val elements: List<TomlValue>
}

interface TomlTable : TomlKeyValueOwner, TomlHeaderOwner
interface TomlArrayTable : TomlKeyValueOwner, TomlHeaderOwner
interface TomlInlineTable : TomlKeyValueOwner

interface TomlTableHeader : TomlValue {
    val names: List<TomlKey>
}

