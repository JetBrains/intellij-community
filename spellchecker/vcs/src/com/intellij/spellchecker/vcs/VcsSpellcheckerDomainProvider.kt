// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.vcs

import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.psi.PsiElement
import com.intellij.spellchecker.statistics.SpellcheckerDomainProvider

internal class VcsSpellcheckerDomainProvider : SpellcheckerDomainProvider {
  override fun getDomain(element: PsiElement): String? {
    return if (CommitMessage.isCommitMessage(element)) "commit" else null
  }
}
