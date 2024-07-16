// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.endOffset
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.Nls
import org.jetbrains.yaml.YAMLBundle
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.YAMLTokenTypes
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YamlPsiElementVisitor
import java.util.function.Consumer

class YAMLDuplicatedKeysInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : YamlPsiElementVisitor() {
      override fun visitMapping(mapping: YAMLMapping) {
        val occurrences = MultiMap<String, YAMLKeyValue>()

        for (keyValue in mapping.keyValues) {
          val keyName = keyValue.keyText.trim { it <= ' ' }
          // http://yaml.org/type/merge.html
          if (keyName == "<<") {
            continue
          }
          if (!keyName.isEmpty()) {
            occurrences.putValue(keyName, keyValue)
          }
        }

        for ((key, value) in occurrences.entrySet()) {
          if (value.size > 1) {
            val allObjects = value.all { it.value is YAMLMapping }
            val allLists = value.all { it.value is YAMLSequence }
            val fixes = if (allObjects || allLists) arrayOf(MergeDuplicatedSectionsQuickFix(), RemoveDuplicatedKeyQuickFix()) else arrayOf(RemoveDuplicatedKeyQuickFix())
            value.forEach(Consumer { duplicatedKey: YAMLKeyValue ->
              checkNotNull(duplicatedKey.key)
              checkNotNull(duplicatedKey.parentMapping) { "This key is gotten from mapping" }
              holder.registerProblem(duplicatedKey.key!!,
                                     YAMLBundle.message("YAMLDuplicatedKeysInspection.duplicated.key", key),
                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                     *fixes)
            })
          }
        }
      }
    }
  }

  private class MergeDuplicatedSectionsQuickFix : LocalQuickFix {
    override fun getFamilyName(): String {
      return YAMLBundle.message("YAMLDuplicatedKeysInspection.merge.quickfix.name")
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val keyVal = descriptor.psiElement.parent as? YAMLKeyValue ?: return
      mergeDuplicates(YAMLElementGenerator(project), keyVal)
    }

    private fun mergeDuplicates(generator: YAMLElementGenerator, keyVal: YAMLKeyValue) {
      val parentMapping = keyVal.parentMapping ?: return
      val key = keyVal.keyText
      val allProps = parentMapping.keyValues.filter { it.keyText == key }
      if (allProps.size <= 1) return
      val firstProperty = allProps[0]
      var hadMerges = false
      allProps.drop(1).forEach {
        val mapping = firstProperty.value as? YAMLMapping
        val sequence = firstProperty.value as? YAMLSequence
        if (mapping != null && mergeMappings(mapping, it, generator)
            || sequence != null && mergeSequences(sequence, it, generator)) {
          hadMerges = true
          deleteWithPrecedingEol(it)
        }
      }
      if (hadMerges) {
        parentMapping.keyValues.firstOrNull { it.keyText == key }?.let {
          PsiEditorUtil.findEditor(it)?.caretModel?.moveToOffset(it.endOffset)
        }
      }
    }

    private fun mergeMappings(mapping: YAMLMapping,
                              it: YAMLKeyValue,
                              generator: YAMLElementGenerator): Boolean {
      val currentMapping = it.value as? YAMLMapping ?: return false
      currentMapping.keyValues.forEach { pp ->
        mapping.getKeyValueByKey(pp.keyText)?.let {
          if (it.value?.text == pp.value?.text) return@forEach
        }
        if (pp.value != null) {
          val newProp = generator.createYamlKeyValue(pp.name!!, "foo").also { p ->
            p.value!!.replace(pp.value!!)
          }
          val eol = mapping.addAfter(generator.createEol(), mapping.keyValues.last())
          val addedProp = mapping.addAfter(newProp, eol) as? YAMLKeyValue
          addedProp?.let {
            mergeDuplicates(generator, addedProp)
          }
        }
        deleteWithPrecedingEol(pp)
      }
      return true
    }

    private fun mergeSequences(sequence: YAMLSequence,
                               it: YAMLKeyValue,
                               generator: YAMLElementGenerator): Boolean {
      val currentSequence = it.value as? YAMLSequence ?: return false
      currentSequence.items.forEach { pp ->
        if (pp.value != null) {
          if (sequence.items.any { it.value?.text == pp.value?.text }) return@forEach
          val newItem = generator.createSequenceItem("foo").also { p ->
            p.value!!.replace(pp.value!!)
          }
          val eol = sequence.addAfter(generator.createEol(), sequence.items.last())
          sequence.addAfter(newItem, eol)
        }
        deleteWithPrecedingEol(pp)
      }
      return true
    }

    private fun deleteWithPrecedingEol(it: PsiElement) {
      val prevSibling = it.prevSibling
      it.delete()
      if (prevSibling.elementType == YAMLTokenTypes.EOL) {
        prevSibling.delete()
      }
    }
  }

  private class RemoveDuplicatedKeyQuickFix : LocalQuickFix {
    override fun getFamilyName(): @Nls String {
      return YAMLBundle.message("YAMLDuplicatedKeysInspection.remove.key.quickfix.name")
    }

    override fun availableInBatchMode(): Boolean {
      //IDEA-185914: quick fix is disabled in batch mode cause of ambiguity which item should stay
      return false
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val keyVal = descriptor.psiElement.parent as? YAMLKeyValue ?: return
      keyVal.parentMapping?.deleteKeyValue(keyVal)
    }
  }
}
