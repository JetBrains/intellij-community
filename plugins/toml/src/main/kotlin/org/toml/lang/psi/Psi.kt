/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi

import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceContributor

interface TomlElement : PsiElement

// region abstract base types

interface TomlValue : TomlElement

interface TomlKeyValueOwner : TomlElement {
    val entries: List<TomlKeyValue>
}

interface TomlHeaderOwner : TomlElement {
    val header: TomlTableHeader
}

// endregion

interface TomlKeyValue : TomlElement {
    val key: TomlKey
    val value: TomlValue?
}

/**
 * It's possible to use [PsiReferenceContributor] to inject references
 * into [TomlKey] from third-party plugins.
 */
interface TomlKey : TomlElement, ContributedReferenceHost

/**
 * It's possible to use [PsiReferenceContributor] to inject references
 * into [TomlLiteral] from third-party plugins.
 */
interface TomlLiteral : TomlValue, ContributedReferenceHost

interface TomlArray : TomlValue {
    val elements: List<TomlValue>
}

interface TomlTable : TomlKeyValueOwner, TomlHeaderOwner
interface TomlArrayTable : TomlKeyValueOwner, TomlHeaderOwner
interface TomlInlineTable : TomlKeyValueOwner, TomlValue

interface TomlTableHeader : TomlElement {
    val names: List<TomlKey>
}

