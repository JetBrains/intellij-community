package org.intellij.plugins.markdown.lang.references.paths.github

import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceWrapper

/**
 * See [MarkdownUnresolvedFileReferenceInspection.shouldSkip].
 */
internal class GithubWikiLocalFileReference(originalReference: PsiReference): PsiReferenceWrapper(originalReference)
