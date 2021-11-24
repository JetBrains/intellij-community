/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.json

import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalkerFactory
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import org.toml.lang.psi.TomlFile

class TomlJsonLikePsiWalkerFactory : JsonLikePsiWalkerFactory {
    override fun handles(element: PsiElement): Boolean = element.containingFile is TomlFile
    override fun create(schemaObject: JsonSchemaObject): JsonLikePsiWalker = TomlJsonPsiWalker
}
