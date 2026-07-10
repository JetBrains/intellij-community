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
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.util.ArrayUtil
import com.intellij.util.ProcessingContext
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeSpan
import org.intellij.plugins.markdown.lang.references.backtick.BacktickReference

private const val ID = "[a-zA-Z_$][a-zA-Z0-9_$]*+"
private const val DOT_QUALIFIED_TAIL = "(?:\\.$ID)++"
private const val MEMBER_SEPARATOR_WITH_OPTIONAL_NAME = "[#.](?:$ID)?"

private val FQN_LIKE_PATTERN = Regex(
  "^$ID(?:$DOT_QUALIFIED_TAIL(?:$MEMBER_SEPARATOR_WITH_OPTIONAL_NAME)?|$MEMBER_SEPARATOR_WITH_OPTIONAL_NAME)$"
)

internal class JvmBacktickReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(MarkdownCodeSpan::class.java),
      object : PsiReferenceProvider() {
        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
          val codeSpan = element as? MarkdownCodeSpan
          val contentRange = codeSpan?.getContentRange() ?: return PsiReference.EMPTY_ARRAY
          val content = contentRange.substring(codeSpan.text)
          if (content.isBlank()) return PsiReference.EMPTY_ARRAY
          if (content.length > 512) return PsiReference.EMPTY_ARRAY

          val bombedCs = object : StringUtil.BombedCharSequence(content) {
            override fun checkCanceled() {
              ProgressManager.checkCanceled()
            }
          }
          if (!bombedCs.matches(FQN_LIKE_PATTERN)) return PsiReference.EMPTY_ARRAY
          val splitIndex = content.getSplitIndex()
          val className = if (splitIndex >= 0) content.substring(0, splitIndex) else content

          val scope = GlobalSearchScope.projectScope(element.project)
          val classReferences = getClassReferences(codeSpan, className, content, contentRange, splitIndex, scope)
          if (splitIndex < 0) return classReferences

          val start = contentRange.startOffset + splitIndex + 1
          val range = TextRange(start, start + content.substring(splitIndex + 1).length)
          return ArrayUtil.append(
            classReferences, JvmMemberReference(codeSpan, range, className, scope),
            PsiReference::class.java
          )
        }

        private fun String.getSplitIndex(): Int {
          var splitIndex = this.indexOf('#')
          if (splitIndex >= 0) return splitIndex
          if (this.last() == '.') return this.length - 1

          splitIndex = this.lastIndexOf('.')
          if (this.looksLikeClassName()) return splitIndex
          val capitalIndex = this.indexOfFirst { it.isUpperCase() }
          return if (capitalIndex != -1 && capitalIndex < splitIndex) splitIndex else -1
        }

        private fun getClassReferences(
          codeSpan: MarkdownCodeSpan, className: String, content: String, contentRange: TextRange, splitIndex: Int, scope: GlobalSearchScope
        ): Array<PsiReference> {
          val provider = object : JavaClassReferenceProvider() {
            override fun getScope(project: Project): GlobalSearchScope = scope
          }
          provider.setOption(JavaClassReferenceProvider.RESOLVE_QUALIFIED_CLASS_NAME, true)
          provider.setOption(JavaClassReferenceProvider.ADVANCED_RESOLVE, true)
          provider.isSoft = true

          val classReferences = provider.getReferencesByString(className, codeSpan, contentRange.startOffset)
          if (splitIndex < 0) return classReferences
          if (!content.looksLikeClassName()) return classReferences
          return ArrayUtil.append(
            classReferences,
            BacktickReference(codeSpan, TextRange(contentRange.startOffset, contentRange.startOffset + splitIndex)),
            PsiReference::class.java
          )
        }

        // Approximation to detect if the content is a class (capital letter) or package (lowercase letter)
        private fun String.looksLikeClassName() = this.first().isUpperCase()
      }
    )
  }

  private class JvmMemberReference(
    element: PsiElement, rangeInElement: TextRange,
    private val className: String, private val scope: GlobalSearchScope,
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
      val psiClasses = getClasses()
      if (psiClasses.isEmpty()) return ResolveResult.EMPTY_ARRAY

      val methodOrFieldName = value
      val results = mutableListOf<PsiElement>()
      psiClasses.forEach { psiClass ->
        results.addAll(psiClass.findMethodsByName(methodOrFieldName, false))
        psiClass.findFieldByName(methodOrFieldName, false)?.let { results.add(it) }
      }
      return results.map { PsiElementResolveResult(it) }.toTypedArray()
    }

    override fun getVariants(): Array<PsiElement> {
      val psiClasses = getClasses()
      if (psiClasses.isEmpty()) return PsiElement.EMPTY_ARRAY

      val results = mutableListOf<PsiNameIdentifierOwner>()
      psiClasses.forEach { psiClass ->
        results.addAll(psiClass.methods.toList())
        results.addAll(psiClass.fields.toList())
      }
      return results.distinctBy { it.name }.toTypedArray()
    }

    private fun getClasses(): Array<PsiClass> {
      if (className.contains('.')) {
        return JavaPsiFacade.getInstance(element.project).findClass(className, scope)?.let { arrayOf(it) } ?: emptyArray()
      }
      return PsiShortNamesCache.getInstance(element.project).getClassesByName(className, scope)
    }
  }
}
