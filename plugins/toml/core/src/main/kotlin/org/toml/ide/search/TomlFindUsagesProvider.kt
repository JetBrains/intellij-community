/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.search

import com.intellij.lang.HelpID
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement

/**
 * This basic provider just enables `Find Usages` action for TOML language. Implement
 * [com.intellij.find.findUsages.FindUsagesHandlerFactory],
 * [com.intellij.psi.ElementDescriptionProvider] and
 * [com.intellij.usages.impl.rules.UsageTypeProviderEx]
 * in your plugin to make find usages for your TOML use-case.
 */
class TomlFindUsagesProvider : FindUsagesProvider {
    /** If null, use default [com.intellij.lang.cacheBuilder.SimpleWordsScanner] here */
    override fun getWordsScanner(): WordsScanner? = null

    override fun canFindUsagesFor(element: PsiElement): Boolean = false

    override fun getHelpId(element: PsiElement): String = HelpID.FIND_OTHER_USAGES

    override fun getType(element: PsiElement): String = ""
    override fun getDescriptiveName(element: PsiElement): String = ""
    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = ""
}
