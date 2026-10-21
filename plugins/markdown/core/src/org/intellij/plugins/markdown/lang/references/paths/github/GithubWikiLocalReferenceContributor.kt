package org.intellij.plugins.markdown.lang.references.paths.github

import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import org.intellij.plugins.markdown.lang.references.ReferenceUtil

@Suppress("SplitModeApiUsage") // the reference reads the VFS only, so a light process without a backend keeps it
internal class GithubWikiLocalReferenceContributor: PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(ReferenceUtil.linkDestinationPattern, GithubWikiLocalFileReferenceProvider())
  }
}
