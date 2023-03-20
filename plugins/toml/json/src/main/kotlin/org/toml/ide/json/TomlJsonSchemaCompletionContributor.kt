/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.json

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.Consumer
import com.intellij.util.ThreeState
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaDocumentationProvider
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver
import com.jetbrains.jsonSchema.impl.JsonSchemaType
import org.toml.ide.experiments.TomlExperiments
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTableHeader
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

class TomlJsonSchemaCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (!TomlExperiments.isJsonSchemaEnabled) return

        val position = parameters.position
        val jsonSchemaService = JsonSchemaService.Impl.get(position.project)
        val jsonSchemaObject = jsonSchemaService.getSchemaObject(parameters.originalFile)

        if (jsonSchemaObject != null) {
            val completionPosition = parameters.originalPosition ?: parameters.position
            val worker = Worker(jsonSchemaObject, parameters.position, completionPosition, result)
            worker.work()
        }
    }

    private class Worker(
        private val rootSchema: JsonSchemaObject,
        private val position: PsiElement,
        private val originalPosition: PsiElement,
        private val resultConsumer: Consumer<LookupElement?>
    ) {
        val variants: MutableSet<LookupElement> = mutableSetOf()
        private val walker: JsonLikePsiWalker? = JsonLikePsiWalker.getWalker(position, rootSchema)
        private val project: Project = originalPosition.project

        fun work() {
            val checkable = walker?.findElementToCheck(position) ?: return
            val isName = walker.isName(checkable)
            val pointerPosition = walker.findPosition(checkable, isName == ThreeState.NO)
            if (pointerPosition == null || pointerPosition.isEmpty && isName == ThreeState.NO) return

            val schemas = JsonSchemaResolver(project, rootSchema, pointerPosition).resolve()
            val knownNames = hashSetOf<String>()

            for (schema in schemas) {
                if (isName != ThreeState.NO) {
                    val properties = walker.getPropertyNamesOfParentObject(originalPosition, position)
                    val adapter = walker.getParentPropertyAdapter(checkable)

                    val schemaProperties = schema.properties
                    addAllPropertyVariants(properties, adapter, schemaProperties, knownNames, originalPosition)
                }

                if (isName != ThreeState.YES) {
                    suggestValues(schema, isName == ThreeState.NO)
                }
            }

            for (variant in variants) {
                resultConsumer.consume(variant)
            }
        }

        private fun addAllPropertyVariants(
            properties: Collection<String>,
            adapter: JsonPropertyAdapter?,
            schemaProperties: Map<String, JsonSchemaObject>,
            knownNames: MutableSet<String>,
            originalPosition: PsiElement
        ) {
            val variants = schemaProperties.keys.filter { name ->
                !properties.contains(name) && !knownNames.contains(name) || name == adapter?.name
            }

            for (variant in variants) {
                knownNames.add(variant)
                val jsonSchemaObject = schemaProperties[variant]

                if (jsonSchemaObject != null) {
                    // skip basic types keys as they can't be in the table header
                    val isTomlHeader = originalPosition.parentOfType<TomlTableHeader>() != null

                    if (isTomlHeader && jsonSchemaObject.guessType() !in JSON_COMPOUND_TYPES) continue

                    addPropertyVariant(variant, jsonSchemaObject)
                }
            }
        }

        @Suppress("NAME_SHADOWING")
        private fun addPropertyVariant(key: String, jsonSchemaObject: JsonSchemaObject) {
            val currentVariants = JsonSchemaResolver(project, jsonSchemaObject).resolve()
            val jsonSchemaObject = currentVariants.firstOrNull() ?: jsonSchemaObject

            var description = JsonSchemaDocumentationProvider.getBestDocumentation(true, jsonSchemaObject)
            if (description.isNullOrBlank()) {
                description = jsonSchemaObject.getTypeDescription(true).orEmpty()
            }

            val lookupElement = LookupElementBuilder.create(key)
                .withTypeText(description)
                .withIcon(getIconForType(jsonSchemaObject.guessType()))

            variants.add(lookupElement)
        }

        private val isInsideStringLiteral: Boolean
            get() = (position.parent as? TomlLiteral)?.kind is TomlLiteralKind.String

        private fun suggestValues(schema: JsonSchemaObject, isSurelyValue: Boolean) {
            val enumVariants = schema.enum
            if (enumVariants != null) {
                for (o in enumVariants) {
                    if (isInsideStringLiteral && o !is String) continue

                    val variant = if (isInsideStringLiteral) {
                        StringUtil.unquoteString(o.toString())
                    } else {
                        o.toString()
                    }
                    variants.add(LookupElementBuilder.create(variant))
                }
            } else if (isSurelyValue && !isInsideStringLiteral) {
                variants.addAll(suggestValuesByType(schema.guessType()))
            }
        }

        private fun suggestValuesByType(type: JsonSchemaType?): List<LookupElement> = when (type) {
            JsonSchemaType._object -> listOf(buildPairLookupElement("{}"))
            JsonSchemaType._array -> listOf(buildPairLookupElement("[]"))
            JsonSchemaType._string -> listOf(buildPairLookupElement("\"\""))
            JsonSchemaType._boolean -> listOf("true", "false").map { LookupElementBuilder.create(it) }
            else -> emptyList()
        }

        private fun buildPairLookupElement(element: String): LookupElement {
            val builder = LookupElementBuilder.create(element)
                .withInsertHandler { context, _ ->
                    EditorModificationUtil.moveCaretRelatively(context.editor, -1)
                }
            return PrioritizedLookupElement.withPriority(builder, LOW_PRIORITY)
        }

        private fun getIconForType(type: JsonSchemaType?) = when (type) {
            JsonSchemaType._object -> AllIcons.Json.Object
            JsonSchemaType._array -> AllIcons.Json.Array
            else -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Property)
        }
    }

    companion object {

        private const val LOW_PRIORITY: Double = -1000.0

        private val JSON_COMPOUND_TYPES = listOf(
            JsonSchemaType._array, JsonSchemaType._object,
            JsonSchemaType._any, null // type is uncertain
        )
    }
}
