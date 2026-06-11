package com.intellij.markdown.java

import com.intellij.codeInspection.reference.PsiMemberReference
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ArrayUtil
import com.intellij.util.ProcessingContext
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeSpan

private val FQN_LIKE_PATTERN = Regex(
  "^[a-zA-Z_$][a-zA-Z0-9_$]*+(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*+)++(?:#(?:[a-zA-Z_$][a-zA-Z0-9_$]*)?)?$"
)

internal class JvmBacktickReferenceContributor: PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(MarkdownCodeSpan::class.java),
      object : PsiReferenceProvider() {
        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
          val codeSpan = element as? MarkdownCodeSpan
          val contentRange = codeSpan?.getContentRange() ?: return PsiReference.EMPTY_ARRAY
          val content = contentRange.substring(codeSpan.text)
          if (content.isBlank()) return PsiReference.EMPTY_ARRAY

          val bombedCs = object : StringUtil.BombedCharSequence(content) {
            override fun checkCanceled() {
              ProgressManager.checkCanceled()
            }
          }
          if (!bombedCs.matches(FQN_LIKE_PATTERN)) return PsiReference.EMPTY_ARRAY
          val hashIndex = content.indexOf('#')
          val classPart = if (hashIndex >= 0) content.substring(0, hashIndex) else content

          val scope = codeSpan.resolveScope
          val provider = object : JavaClassReferenceProvider() {
            override fun getScope(project: Project): GlobalSearchScope = scope
          }
          provider.setOption(JavaClassReferenceProvider.RESOLVE_QUALIFIED_CLASS_NAME, true)
          provider.setOption(JavaClassReferenceProvider.ADVANCED_RESOLVE, true)
          provider.isSoft = true

          val classReferences = provider.getReferencesByString(classPart, codeSpan, contentRange.startOffset)
          if (hashIndex < 0) return classReferences

          val psiClass = lazy { JavaPsiFacade.getInstance(codeSpan.project).findClass(classPart, scope) }
          val start = contentRange.startOffset + hashIndex + 1
          val range = TextRange(start, start + content.substring(hashIndex + 1).length)
          return ArrayUtil.append(
            classReferences, JvmMemberReference(codeSpan, range, psiClass),
            PsiReference::class.java
          )
        }
      }
    )
  }

  private class JvmMemberReference(
    element: PsiElement,
    rangeInElement: TextRange,
    private val psiClassProvider: Lazy<PsiClass?>,
  ) : PsiPolyVariantReferenceBase<PsiElement>(element, rangeInElement, true), PsiMemberReference {

    private object Resolver : ResolveCache.PolyVariantResolver<JvmMemberReference> {
      override fun resolve(ref: JvmMemberReference, incompleteCode: Boolean): Array<ResolveResult> {
        return ref.tryResolve()
      }
    }

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
      val file = element.containingFile
      return ResolveCache.getInstance(file.project).resolveWithCaching(this, Resolver, true, incompleteCode, file)
    }

    private fun tryResolve(): Array<ResolveResult> {
      val psiClass = psiClassProvider.value ?: return ResolveResult.EMPTY_ARRAY
      val name = value
      val results = mutableListOf<PsiElement>()
      results.addAll(psiClass.findMethodsByName(name, false))
      psiClass.findFieldByName(name, false)?.also { results.add(it) }
      return results.map { PsiElementResolveResult(it) }.toTypedArray()
    }

    override fun getVariants(): Array<PsiElement> {
      val psiClass = psiClassProvider.value ?: return PsiElement.EMPTY_ARRAY
      return (psiClass.methods.distinctBy { it.name } + psiClass.fields.asList()).toTypedArray()
    }
  }
}
