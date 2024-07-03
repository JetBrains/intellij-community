// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.printing

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.symbols.*
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class JKSymbolRenderer(private val importStorage: JKImportStorage, project: Project) {
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
        if (isTopLevelBuiltInKotlinFunction) return true
        return this is JKClassSymbol || isStaticMember || isEnumConstant
    }

    fun renderSymbol(symbol: JKSymbol, owner: JKTreeElement?): String {
        val name = symbol.name.escaped()
        val fqName = symbol.getDisplayFqName().escapedAsQualifiedName()
        val target = symbol.target

        return when {
            !symbol.isFqNameExpected(owner) -> {
                if (symbol.isTopLevelBuiltInKotlinFunction && importStorage.isImportNeeded(fqName)) {
                    // Kotlin's extension function is called by a simple name, but the import still needs to be added
                    importStorage.addImport(fqName)
                }

                name
            }

            symbol.isFromJavaLangPackage() -> fqName

            // if this symbol is an inner type referenced in an outer class's header, refer to it with the qualified name
            // e.g. `Outer.Nested` in `class Outer<T : Outer.Nested?> : Comparable<Outer.Nested?>`
            (owner is JKInheritanceInfo || (owner?.parentOfType<JKTypeParameterList>() != null)) &&
                    target is JKClass && owner.isDefinedInSameClass(target) -> fqName

            symbol is JKClassSymbol && canBeShortenedClassNameCache.canBeShortened(symbol) -> {

                // If a type's definition and reference live in the same file, no import is needed as long as we follow the next 3 rules:
                //   1. If a type's definition is a parent node of the reference, we can use the type's short name
                //   2. If a type's definition and reference share the same direct parent node, we can use the type's short name
                //   3. If a type's definition and reference share a non-direct parent node, we can use the type's semi-qualified name
                if (target !is JKClass) {
                    importStorage.addImport(fqName)
                    name
                } else if (owner?.findParentOfType<JKClass> { it == target || target in it.declarationList } != null) {
                    name
                } else if (owner?.parentOfType<JKTreeRoot>() == target.parentOfType<JKTreeRoot>()) {
                    if (target.parent is JKFile) name else fqName
                } else {
                    importStorage.addImport(fqName)
                    name
                }
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

        private val JKSymbol.isTopLevelBuiltInKotlinFunction: Boolean
            get() = this is JKMultiverseFunctionSymbol && fqName.isKotlinPackagePrefix && target.parent is KtFile

        private val String.isKotlinPackagePrefix: Boolean
            get() = this == "kotlin" || this.startsWith("kotlin.")

        private fun JKSymbol.isFromJavaLangPackage(): Boolean =
            fqName.startsWith(JAVA_LANG_FQ_PREFIX)

        private fun JKTreeElement.isSelectorOfQualifiedExpression(): Boolean =
            parent?.safeAs<JKQualifiedExpression>()?.selector == this

        private fun JKTreeElement.isDefinedInSameClass(other: JKClass): Boolean =
            this.parentOfType<JKClass>()?.declarationList?.any { it == other } == true
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
