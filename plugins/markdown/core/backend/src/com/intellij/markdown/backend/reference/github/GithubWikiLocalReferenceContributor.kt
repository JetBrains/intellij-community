package com.intellij.markdown.backend.reference.github

import com.intellij.markdown.backend.reference.GithubWikiLocalFileReferenceProvider
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import org.intellij.plugins.markdown.lang.references.ReferenceUtil

internal class GithubWikiLocalReferenceContributor: PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(ReferenceUtil.linkDestinationPattern, GithubWikiLocalFileReferenceProvider())
  }
}
