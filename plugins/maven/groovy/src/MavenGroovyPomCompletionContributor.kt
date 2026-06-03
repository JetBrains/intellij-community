// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.groovy

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionService
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.impl.NegatingComparable
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.icons.AllIcons
import com.intellij.lang.xml.XMLLanguage
import com.intellij.maven.completion.getCompletionContext
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.util.Consumer
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.xml.impl.GenericDomValueReference
import org.jetbrains.idea.maven.dom.MavenDomUtil.POM_COMPLETION_ORIGINAL_FILE
import org.jetbrains.idea.maven.dom.MavenVersionComparable
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil.invokeCompletion
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import java.util.Collections

/**
 * @author Vladislav.Soroka
 */
class MavenGroovyPomCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val position = parameters.position
    if (position !is LeafElement) return

    val project = position.getProject()
    val virtualFile = parameters.originalFile.getVirtualFile()
    if (virtualFile == null) return

    val mavenProject = MavenProjectsManager.getInstance(project).findProject(virtualFile)
    if (mavenProject == null) return

    val methodCallInfo = MavenGroovyPomUtil.getGroovyMethodCalls(position)
    if (methodCallInfo.isEmpty()) return

    val buf = StringBuilder()
    for (s in methodCallInfo) {
      buf.append('<').append(s).append('>')
    }
    for (s in ContainerUtil.reverse(methodCallInfo)) {
      buf.append('<').append(s).append("/>")
    }

    val psiFile = PsiFileFactory.getInstance(project).createFileFromText(MavenConstants.POM_XML, XMLLanguage.INSTANCE, buf)
    psiFile.putUserData(POM_COMPLETION_ORIGINAL_FILE, virtualFile)
    val variants: MutableList<Any> = ArrayList()


    val lastMethodCall = methodCallInfo.lastOrNull()
    val completeDependency = Ref.create<Boolean>(false)
    val completeVersion = Ref.create<Boolean>(false)
    psiFile.accept(object : PsiRecursiveElementVisitor(true) {
      override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        if (!completeDependency.get()!! && element.getParent() is XmlTag &&
            "dependency" == (element.getParent() as XmlTag).getName()
        ) {
          if ("artifactId" == lastMethodCall || "groupId" == lastMethodCall) {
            completeDependency.set(true)
          }
          else if ("version" == lastMethodCall || "dependency" == lastMethodCall) {
            completeVersion.set(true)
          }
        }

        if (!completeDependency.get()!! && !completeVersion.get()!!) {
          val references: Array<PsiReference?> = getReferences(element)
          for (each in references) {
            if (each is GenericDomValueReference<*>) {
              Collections.addAll(variants, *each.getVariants())
            }
          }
        }
      }
    })
    for (variant in variants) {
      if (variant is LookupElement) {
        result.addElement(variant)
      }
      else {
        result.addElement(LookupElementBuilder.create(variant))
      }
    }

    val context = parameters.getCompletionContext()

    if (completeDependency.get()) {
      runBlockingCancellable {
        val seen = mutableSetOf<String>()
        service<DependencyCompletionService>()
          .suggestCompletions(DependencyCompletionRequest("", context))
          .collect { event ->
            if (event !is DependencyCompletionEvent.Item) return@collect
            val depResult = event.result
            val key = "${depResult.groupId}:${depResult.artifactId}"
            if (seen.add(key)) {
              result.addElement(
                LookupElementBuilder.create(key)
                  .withIcon(AllIcons.Nodes.PpLib)
                  .withInsertHandler(MavenDependencyInsertHandler.INSTANCE)
              )
            }
          }
      }
    }

    if (completeVersion.get()) {
      consumeDependencyElement(position, Consumer { closableBlock: GrClosableBlock? ->
        var groupId: String? = null
        var artifactId: String? = null
        for (methodCall in PsiTreeUtil.findChildrenOfType(closableBlock, GrMethodCall::class.java)) {
          val arguments = methodCall.getArgumentList().getAllArguments()
          if (arguments.size != 1) continue
          val reference = arguments[0]!!.getReference()
          if (reference == null) continue

          val callExpression = methodCall.getInvokedExpression().getText()
          val argumentValue = reference.getCanonicalText()
          if ("groupId" == callExpression) {
            groupId = argumentValue
          }
          else if ("artifactId" == callExpression) {
            artifactId = argumentValue
          }
        }
        completeVersions(result, context, groupId, artifactId, "")
      }, Consumer { element: PsiElement? ->
        if (element!!.getParent() is PsiLiteral) {
          val value = (element.getParent() as PsiLiteral).getValue()
          if (value == null) return@Consumer

          val mavenCoordinates = value.toString().split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
          if (mavenCoordinates.size < 3) return@Consumer

          val prefix = mavenCoordinates[0] + ':' + mavenCoordinates[1] + ':'
          completeVersions(result, context, mavenCoordinates[0], mavenCoordinates[1], prefix)
        }
      })
    }
  }

  internal class MavenDependencyInsertHandler : InsertHandler<LookupElement?> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
      val s = item.getLookupString()
      val idx = s.indexOf(':')
      val groupId = s.substring(0, idx)
      val artifactId = s.substring(idx + 1)

      val startOffset = context.getStartOffset()
      val psiFile = context.getFile()
      val psiElement = psiFile.findElementAt(startOffset)

      consumeDependencyElement(psiElement, Consumer { closableBlock: GrClosableBlock? ->
        val textOffset = closableBlock!!.getTextOffset()
        val value = "{groupId '" + groupId + "'\n" +
                    "artifactId '" + artifactId + "'\n" +
                    "version ''}"
        context.document.replaceString(textOffset, textOffset + closableBlock.getTextLength(), value)
        context.editor.getCaretModel().moveToOffset(textOffset + value.length - 2)

        context.commitDocument()
        ReformatCodeProcessor(psiFile.getProject(), psiFile, closableBlock.getTextRange(), false).run()
        invokeCompletion(context, CompletionType.BASIC)
      }, Consumer { element: PsiElement? ->
        val textOffset = element!!.getTextOffset()
        val value = "'$groupId:$artifactId:'"
        context.document.replaceString(textOffset, textOffset + element.getTextLength(), value)
        context.editor.getCaretModel().moveToOffset(textOffset + value.length - 1)
        invokeCompletion(context, CompletionType.BASIC)
      })
    }

    companion object {
      val INSTANCE: InsertHandler<LookupElement?> = MavenDependencyInsertHandler()
    }
  }

}

private fun completeVersions(
  completionResultSet: CompletionResultSet,
  context: DependencyCompletionContext,
  groupId: String?,
  artifactId: String?,
  prefix: String,
) {
  if (artifactId.isNullOrBlank()) return
  val newResultSet = completionResultSet.withRelevanceSorter(
    CompletionService.getCompletionService().emptySorter().weigh(
      object : LookupElementWeigher("mavenVersionWeigher") {
        override fun weigh(element: LookupElement): Comparable<*> {
          return NegatingComparable(MavenVersionComparable(element.getLookupString().removePrefix(prefix)))
        }
      })
  )

  if (!groupId.isNullOrBlank()) {
    runBlockingCancellable {
      service<DependencyCompletionService>()
        .suggestVersionCompletions(DependencyVersionCompletionRequest(groupId, artifactId, "", context))
        .collect { event ->
          if (event !is DependencyCompletionEvent.Item) return@collect
          val result = event.result
          newResultSet.addElement(LookupElementBuilder.create(prefix + result.result))
        }
    }
  }

  newResultSet.addElement(LookupElementBuilder.create(prefix + RepositoryLibraryDescription.ReleaseVersionId))
  newResultSet.addElement(LookupElementBuilder.create(prefix + RepositoryLibraryDescription.LatestVersionId))
}

private fun getReferences(psiElement: PsiElement): Array<PsiReference?> {
  return if (psiElement is XmlText) psiElement.getParent().getReferences() else psiElement.getReferences()
}


private fun consumeDependencyElement(
  psiElement: PsiElement?,
  closureNotationConsumer: Consumer<GrClosableBlock>,
  stringNotationConsumer: Consumer<PsiElement>,
) {
  val owner = PsiTreeUtil.getParentOfType(psiElement, GrClosableBlock::class.java)
  if (owner != null && owner.getParent() is GrMethodCallExpression) {
    val invokedExpressionText = (owner.getParent() as GrMethodCallExpression).getInvokedExpression().getText()
    if ("dependency" == invokedExpressionText) {
      closureNotationConsumer.consume(owner)
    }
    if ("dependencies" == invokedExpressionText) {
      val methodCall = PsiTreeUtil.getParentOfType(psiElement, GrMethodCall::class.java)
      if (methodCall != null && "dependency" == methodCall.getInvokedExpression().getText()) {
        stringNotationConsumer.consume(psiElement)
      }
    }
  }
}