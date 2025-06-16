package com.intellij.lang.properties.spellchecker.handler

import com.intellij.lang.properties.psi.impl.PropertyKeyImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.spellchecker.handler.SpellcheckingElementHandler

class PropertySpellcheckingHandler : SpellcheckingElementHandler {

  override fun isEligibleForRenaming(psiElement: PsiElement): Boolean = psiElement is PropertyKeyImpl

  override fun getNamedElement(psiElement: PsiElement): PsiNamedElement? = psiElement.parent as PsiNamedElement
}