/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.json

import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlKeyValueOwner

class TomlJsonPropertyAdapter(private val keyValue: TomlKeyValue) : JsonPropertyAdapter {
    override fun getName(): String = keyValue.key.segments.lastOrNull()?.name ?: ""
    override fun getNameValueAdapter(): JsonValueAdapter = TomlJsonGenericValueAdapter(keyValue.key)
    override fun getDelegate(): PsiElement = keyValue

    override fun getValues(): Collection<JsonValueAdapter> {
        val value = keyValue.value ?: return emptyList()
        return listOf(TomlJsonValueAdapter.createAdapterByType(value))
    }

    override fun getParentObject(): JsonObjectValueAdapter? {
        val parent = keyValue.parent
        if (parent !is TomlKeyValueOwner) return null

        return TomlJsonObjectAdapter(parent)
    }
}
