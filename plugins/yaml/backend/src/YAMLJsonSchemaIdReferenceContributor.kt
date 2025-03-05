// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons.FileTypes.JsonSchema
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.intellij.util.asSafely
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaByCommentProvider
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLMapping

class YAMLJsonSchemaIdReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiComment().with(TopDocumentComment), object : PsiReferenceProvider() {
      override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val charSequence = StringUtil.newBombedCharSequence(element.node.chars, 300)
        val inComment = JsonSchemaByCommentProvider.detectInComment(charSequence) ?: return emptyArray()
        return arrayOf(JsonSchemaIdReference(element, inComment))
      }
    }
    )
  }
}

private object TopDocumentComment : PatternCondition<PsiComment?>("inTopOfDocument") {
  override fun accepts(t: PsiComment, context: ProcessingContext?): Boolean {
    var element: PsiElement = t
    element = element.parent.takeIf { it is YAMLMapping } ?: element
    element = element.parent.takeIf { it is YAMLDocument } ?: element
    return element.parent is PsiFile
  }
}

class JsonSchemaIdReference(element: PsiElement,
                            rangeInElement: TextRange) : PsiReferenceBase.Immediate<PsiElement>(element, rangeInElement, true, element) {

  override fun getVariants(): Array<Any> {
    val project = this.element.project
    val schemaService = JsonSchemaService.Impl.get(project)
    return schemaService.allUserVisibleSchemas
      .filter { it.provider == null || it.provider?.remoteSource != null }
      .map { jsonSchemaInfo ->
        val url = jsonSchemaInfo.getUrl(project)
        LookupElementBuilder.create(url).withIcon(JsonSchema)
      }.toTypedArray()

  }
}

class YAMLJsonSchemaInCommentCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiComment().with(TopDocumentComment),
           object : CompletionProvider<CompletionParameters?>() {
             override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {

               val psiComment = parameters.originalPosition?.asSafely<PsiComment>() ?: return
               val trimmedBody = psiComment.text.removePrefix("#").trimStart()

               for (schemaComment in JsonSchemaByCommentProvider.schemaCommentsForCompletion) {
                 if (trimmedBody.isBlank() ||
                     trimmedBody.length != schemaComment.length && schemaComment.startsWith(trimmedBody, true)) {
                   result.addElement(
                     LookupElementBuilder.create(schemaComment)
                       .withIcon(JsonSchema)
                       .withTypeText("JSON Schema specifying comment", true)
                       .withInsertHandler(
                         InsertHandler { context: InsertionContext, item: LookupElement? ->
                           context.laterRunnable = Runnable {
                             CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(context.project, context.editor)
                           }
                         })
                   )
                 }

               }

             }
           })
  }
}