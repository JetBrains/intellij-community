/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.json

import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import org.toml.lang.psi.*
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

sealed class TomlJsonValueAdapter<T : TomlElement>(protected val element: T) : JsonValueAdapter {
    final override fun getDelegate(): PsiElement = element

    override fun isObject(): Boolean = false
    override fun isArray(): Boolean = false
    override fun isNull(): Boolean = false
    override fun isStringLiteral(): Boolean = false
    override fun isNumberLiteral(): Boolean = false
    override fun isBooleanLiteral(): Boolean = false

    override fun getAsObject(): JsonObjectValueAdapter? = null
    override fun getAsArray(): JsonArrayValueAdapter? = null

    companion object {
        fun createAdapterByType(value: TomlElement): TomlJsonValueAdapter<*> = when (value) {
            is TomlKeyValueOwner -> TomlJsonObjectAdapter(value)
            is TomlArray -> TomlJsonArrayAdapter(value)
            else -> TomlJsonGenericValueAdapter(value)
        }
    }
}

class TomlJsonGenericValueAdapter(value: TomlElement) : TomlJsonValueAdapter<TomlElement>(value) {
    override fun isStringLiteral(): Boolean =
        element is TomlLiteral && element.kind is TomlLiteralKind.String || element is TomlKey

    override fun isNumberLiteral(): Boolean = element is TomlLiteral && element.kind is TomlLiteralKind.Number
    override fun isBooleanLiteral(): Boolean = element is TomlLiteral && element.kind is TomlLiteralKind.Boolean
}

class TomlJsonArrayAdapter(array: TomlArray) : TomlJsonValueAdapter<TomlArray>(array), JsonArrayValueAdapter {
    private val childAdapters by lazy {
        element.elements.map { createAdapterByType(it) }
    }

    override fun isArray(): Boolean = true
    override fun isNull(): Boolean = false

    override fun getAsArray(): JsonArrayValueAdapter = this
    override fun getElements(): List<JsonValueAdapter> = childAdapters
}

class TomlJsonObjectAdapter(keyValueOwner: TomlKeyValueOwner) : TomlJsonValueAdapter<TomlKeyValueOwner>(keyValueOwner), JsonObjectValueAdapter {
    private val childAdapters by lazy {
        element.entries.map { TomlJsonPropertyAdapter(it) }
    }

    override fun isObject(): Boolean = true
    override fun isNull(): Boolean = false

    override fun getAsObject(): JsonObjectValueAdapter = this
    override fun getPropertyList(): List<JsonPropertyAdapter> = childAdapters
}
