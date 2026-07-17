package org.intellij.plugins.markdown.lang.references.paths.github

import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceWrapper
import org.jetbrains.annotations.ApiStatus

/**
 * See [MarkdownUnresolvedFileReferenceInspection.shouldSkip].
 */
@ApiStatus.Internal
class GithubWikiLocalFileReference(originalReference: PsiReference): PsiReferenceWrapper(originalReference)
