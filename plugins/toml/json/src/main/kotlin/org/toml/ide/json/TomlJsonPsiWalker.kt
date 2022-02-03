/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.json

import com.intellij.json.pointer.JsonPointerPosition
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import org.toml.lang.psi.*

object TomlJsonPsiWalker : JsonLikePsiWalker {
    override fun isName(element: PsiElement?): ThreeState =
        if (element is TomlKeySegment) ThreeState.YES else ThreeState.NO

    override fun isPropertyWithValue(element: PsiElement): Boolean =
        element is TomlKeyValue && element.value != null

    override fun findElementToCheck(element: PsiElement): PsiElement? =
        element.parentOfType<TomlElement>()

    override fun findPosition(element: PsiElement, forceLastTransition: Boolean): JsonPointerPosition {
        val position = JsonPointerPosition()
        var current = element

        val tableHeaderSegments = mutableListOf<String?>()
        var nestedIndex: Int? = null

        while (current !is PsiFile) {
            val parent = current.parent

            when {
                current is TomlKeySegment && parent is TomlKey -> {
                    // forceLastTransition as true is used in inspections, whether as false is used in completion
                    // to skip the last segment as it may not have been typed completely yet
                    if (current != element || forceLastTransition) {
                        position.addPrecedingStep(current.name)
                    }
                    for (segment in parent.segments.takeWhile { it != current }.asReversed()) {
                        position.addPrecedingStep(segment.name)
                    }
                }
                current is TomlKeyValue && parent is TomlHeaderOwner -> {
                    val parentKey = parent.header.key ?: break
                    // add table header segments to process all the previous siblings to handle nested array tables cases
                    parentKey.segments.mapTo(tableHeaderSegments) { it.name }
                }
                current is TomlValue && parent is TomlArray -> {
                    if (current != element || forceLastTransition) {
                        position.addPrecedingStep(parent.elements.indexOf(current))
                    }
                }
                current is TomlValue && parent is TomlKeyValue -> {
                    val parentKey = parent.key

                    for (segment in parentKey.segments.asReversed()) {
                        if (segment != element || forceLastTransition) {
                            position.addPrecedingStep(segment.name)
                        }
                    }
                }
                current is TomlHeaderOwner && parent is PsiFile -> {
                    val currentSegments = current.header.key?.segments?.map { it.name }.orEmpty()

                    // lower keys size until it is equals current header key size
                    while (tableHeaderSegments.size > currentSegments.size && ContainerUtil.startsWith(tableHeaderSegments, currentSegments)) {
                        // add index if it is present (meaning that the current segment is array one)
                        if (nestedIndex != null) {
                            position.addPrecedingStep(nestedIndex)
                            nestedIndex = null
                        }
                        position.addPrecedingStep(tableHeaderSegments.removeLast())
                    }

                    // increment array table index if keys are equal
                    if (currentSegments == tableHeaderSegments && current is TomlArrayTable) {
                        if (nestedIndex != null) {
                            nestedIndex += 1
                        } else {
                            nestedIndex = 0
                        }
                    }
                }
            }

            // take previous sibling if it is the top-level item, otherwise use parent
            if (current is TomlHeaderOwner && parent is PsiFile && current.prevSibling != null) {
                // skip everything else than TomlHeaderOwner
                do {
                    current = current.prevSibling
                } while (current !is TomlHeaderOwner && current.prevSibling != null)
            } else {
                current = current.parent
            }
        }

        // process the left segments after last top-element which was equal with them
        while (tableHeaderSegments.isNotEmpty()) {
            if (nestedIndex != null) {
                position.addPrecedingStep(nestedIndex)
                nestedIndex = null
            }
            position.addPrecedingStep(tableHeaderSegments.removeLast())
        }

        return position
    }

    override fun getPropertyNamesOfParentObject(originalPosition: PsiElement, computedPosition: PsiElement?): Set<String> {
        val table = originalPosition.parentOfType<TomlTable>()
            ?: originalPosition.parentOfType<TomlInlineTable>()
            ?: originalPosition.prevSibling as? TomlTable
            ?: return emptySet()
        return TomlJsonObjectAdapter(table).propertyList.mapNotNullTo(HashSet()) { it.name }
    }

    override fun getParentPropertyAdapter(element: PsiElement): JsonPropertyAdapter? {
        val property = element.parentOfType<TomlKeyValue>(true) ?: return null
        return TomlJsonPropertyAdapter(property)
    }

    override fun isTopJsonElement(element: PsiElement): Boolean = element is TomlFile

    override fun createValueAdapter(element: PsiElement): JsonValueAdapter? =
        if (element is TomlElement) TomlJsonValueAdapter.createAdapterByType(element) else null

    override fun getRoots(file: PsiFile): List<PsiElement> {
        if (file !is TomlFile) return emptyList()
        return file.children.toList()
    }

    override fun getPropertyNameElement(property: PsiElement?): PsiElement? = (property as? TomlKeyValue)?.key

    override fun hasMissingCommaAfter(element: PsiElement): Boolean = false

    override fun acceptsEmptyRoot(): Boolean = true

    override fun requiresNameQuotes(): Boolean = false
    override fun allowsSingleQuotes(): Boolean = false
}