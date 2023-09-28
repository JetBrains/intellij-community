// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.printing

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.nj2k.JKImportStorage
import org.jetbrains.kotlin.nj2k.escaped
import org.jetbrains.kotlin.nj2k.symbols.*
import org.jetbrains.kotlin.nj2k.tree.JKQualifiedExpression
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class JKSymbolRenderer(private val importStorage: JKImportStorage, project: Project) {
    private val canBeShortenedClassNameCache = CanBeShortenedCache(project)

    private fun JKSymbol.isFqNameExpected(owner: JKTreeElement?): Boolean {
        if (owner?.isSelectorOfQualifiedExpression() == true) return false
        if (fqName == "kotlin.run") {
            // Unfortunately, there are two overloads with FQN `kotlin.run`: with and without a receiver.
            // So, if we generate a fully qualified call to `kotlin.run`, the reference shortener in a post-processing
            // won't shorten the call to `run`, because it would change the resolution from one function to the other.
            // In principle, it shouldn't matter, because we are generating the `run` calls to introduce a new scope,
            // not to use a receiver. Both `run` and `kotlin.run` calls should work from the J2K point of view, but `run` is cleaner.
            return false
        }
        if (this.safeAs<JKMultiverseFunctionSymbol>()?.isTopLevelBuiltInKotlinFunction == true) return true
        return this is JKClassSymbol || isStaticMember || isEnumConstant
    }

    fun renderSymbol(symbol: JKSymbol, owner: JKTreeElement?): String {
        val name = symbol.name.escaped()
        if (!symbol.isFqNameExpected(owner)) return name
        val fqName = symbol.getDisplayFqName().escapedAsQualifiedName()
        if (symbol.isFromJavaLangPackage()) return fqName

        return when {
            symbol is JKClassSymbol && canBeShortenedClassNameCache.canBeShortened(symbol) -> {
                importStorage.addImport(fqName)
                name
            }

            symbol.isStaticMember && symbol.containingClass?.isUnnamedCompanion == true -> {
                val containingClass = symbol.containingClass ?: return fqName
                val classContainingCompanion = containingClass.containingClass ?: return fqName
                if (!canBeShortenedClassNameCache.canBeShortened(classContainingCompanion)) return fqName
                importStorage.addImport(classContainingCompanion.getDisplayFqName())
                "${classContainingCompanion.name.escaped()}.${SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT}.$name"
            }

            symbol.isEnumConstant || symbol.isStaticMember -> {
                val containingClass = symbol.containingClass ?: return fqName
                if (!canBeShortenedClassNameCache.canBeShortened(containingClass)) return fqName
                importStorage.addImport(containingClass.getDisplayFqName())
                "${containingClass.name.escaped()}.$name"
            }

            else -> fqName
        }
    }

    companion object {
        private const val JAVA_LANG_FQ_PREFIX = "java.lang"

        private val JKMultiverseFunctionSymbol.isTopLevelBuiltInKotlinFunction: Boolean
            get() = fqName.isKotlinPackagePrefix && target.parent is KtFile

        private val String.isKotlinPackagePrefix: Boolean
            get() = this == "kotlin" || this.startsWith("kotlin.")

        private fun JKSymbol.isFromJavaLangPackage(): Boolean =
            fqName.startsWith(JAVA_LANG_FQ_PREFIX)

        private fun JKTreeElement.isSelectorOfQualifiedExpression(): Boolean =
            parent?.safeAs<JKQualifiedExpression>()?.selector == this
    }
}

private class CanBeShortenedCache(project: Project) {
    private val shortNameCache = PsiShortNamesCache.getInstance(project)
    private val searchScope = GlobalSearchScope.allScope(project)
    private val canBeShortenedCache = mutableMapOf<String, Boolean>().apply {
        CLASS_NAMES_WHICH_HAVE_DIFFERENT_MEANINGS_IN_KOTLIN_AND_JAVA.forEach { name ->
            this[name] = false
        }
    }

    fun canBeShortened(symbol: JKClassSymbol): Boolean = canBeShortenedCache.getOrPut(symbol.name) {
        var symbolsWithSuchNameCount = 0
        val processSymbol = { _: PsiClass ->
            symbolsWithSuchNameCount++
            symbolsWithSuchNameCount <= 1 //stop if met more than one symbol with such name
        }
        shortNameCache.processClassesWithName(symbol.name, processSymbol, searchScope, null)
        symbolsWithSuchNameCount == 1
    }

    companion object {
        private val CLASS_NAMES_WHICH_HAVE_DIFFERENT_MEANINGS_IN_KOTLIN_AND_JAVA = setOf(
            "Function",
            "Serializable"
        )
    }
}
