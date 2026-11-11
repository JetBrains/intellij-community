// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.printing

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import org.jetbrains.kotlin.idea.stubindex.KotlinFunctionShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinPropertyShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasShortNameIndex
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.j2k.JKImportStorage
import org.jetbrains.kotlin.j2k.canBeGetterOrSetterName
import org.jetbrains.kotlin.j2k.escaped
import org.jetbrains.kotlin.j2k.symbols.JKClassSymbol
import org.jetbrains.kotlin.j2k.symbols.JKMethodSymbol
import org.jetbrains.kotlin.j2k.symbols.JKMultiverseClassSymbol
import org.jetbrains.kotlin.j2k.symbols.JKMultiverseFunctionSymbol
import org.jetbrains.kotlin.j2k.symbols.JKSymbol
import org.jetbrains.kotlin.j2k.symbols.JKUniverseClassSymbol
import org.jetbrains.kotlin.j2k.symbols.containingClass
import org.jetbrains.kotlin.j2k.symbols.getDisplayFqName
import org.jetbrains.kotlin.j2k.symbols.isEnumConstant
import org.jetbrains.kotlin.j2k.symbols.isStaticMember
import org.jetbrains.kotlin.j2k.symbols.isUnnamedCompanion
import org.jetbrains.kotlin.j2k.tree.JKClass
import org.jetbrains.kotlin.j2k.tree.JKClassBody
import org.jetbrains.kotlin.j2k.tree.JKDeclaration
import org.jetbrains.kotlin.j2k.tree.JKDeclarationStatement
import org.jetbrains.kotlin.j2k.tree.JKQualifiedExpression
import org.jetbrains.kotlin.j2k.tree.JKTreeElement
import org.jetbrains.kotlin.j2k.tree.JKTypeParameterListOwner
import org.jetbrains.kotlin.j2k.tree.parents
import org.jetbrains.kotlin.j2k.types.JKClassType
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class JKSymbolRenderer(private val importStorage: JKImportStorage, project: Project) {
    private val canBeShortenedClassNameCache = CanBeShortenedCache(project)
    private val companionMemberNameIsFreeCache = HashMap<Pair<JKClass, String>, Boolean>()

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

        return when {
            !symbol.isFqNameExpected(owner) -> {
                if (symbol.isTopLevelBuiltInKotlinFunction && importStorage.isImportNeeded(fqName)) {
                    // Kotlin's extension function is called by a simple name, but the import still needs to be added
                    importStorage.addImport(fqName)
                }

                name
            }

            symbol.isFromJavaLangPackage() -> fqName

            symbol is JKClassSymbol && isDefaultImportWithoutConflicts(symbol, owner) -> name

            symbol is JKClassSymbol && canBeShortenedClassNameCache.canBeShortened(symbol) -> {
                importStorage.addImport(fqName)
                name
            }

            symbol.isStaticMember && symbol.containingClass?.isUnnamedCompanion == true -> {
                val containingClass = symbol.containingClass ?: return fqName
                val classContainingCompanion = containingClass.containingClass ?: return fqName
                if (symbol is JKMethodSymbol && owner != null && isCompanionMethodVisibleByName(symbol, containingClass, classContainingCompanion, owner)) {
                    return name
                }
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

    /**
     * Gives the short name to a class of a default import, such as `kotlin.Int`.
     * No other class, type alias or type parameter can have this name.
     */
    private fun isDefaultImportWithoutConflicts(symbol: JKClassSymbol, owner: JKTreeElement?): Boolean {
        if (owner == null || !importStorage.isDefaultImport(symbol.fqName)) return false
        val name = symbol.name
        val hasTypeParameterWithSameName = owner.parents().any { parent ->
            parent is JKTypeParameterListOwner && parent.typeParameterList.typeParameters.any { it.name.value == name }
        }
        return !hasTypeParameterWithSameName && canBeShortenedClassNameCache.hasNoOtherClassifier(symbol)
    }

    /**
     * Gives the short name to a call of a companion method from the body of the companion or of its class.
     * A closer declaration, a member of the class or its supertypes, or a Kotlin extension can have the same name and win the resolution.
     * Each of them keeps the qualified name.
     * A getter or a setter also keeps it, because a post-processing can change its calls to a property access on `this`.
     */
    private fun isCompanionMethodVisibleByName(
        symbol: JKMethodSymbol,
        companion: JKClassSymbol,
        classContainingCompanion: JKClassSymbol,
        owner: JKTreeElement,
    ): Boolean {
        val companionClass = (companion as? JKUniverseClassSymbol)?.target ?: return false
        val outerClass = (classContainingCompanion as? JKUniverseClassSymbol)?.target ?: return false
        val name = symbol.name
        if (name.canBeGetterOrSetterName()) return false
        for (parent in owner.parents()) {
            if (parent is JKClassBody) {
                if (parent.parent !== companionClass && parent.parent !== outerClass) return false
                return companionMemberNameIsFreeCache.getOrPut(outerClass to name) {
                    !outerClass.hasMemberNamed(name, companionClass) && canBeShortenedClassNameCache.hasNoKotlinExtension(name)
                }
            }
            if (parent is JKTreeElement && parent.hasChildDeclarationNamed(name)) return false
        }
        return false
    }

    companion object {
        private const val JAVA_LANG_FQ_PREFIX = "java.lang"

        private fun JKTreeElement.hasChildDeclarationNamed(name: String): Boolean {
            forEachChild { child ->
                val declarations = when (child) {
                    is JKDeclarationStatement -> child.declaredStatements
                    is JKDeclaration -> listOf(child)
                    else -> emptyList()
                }
                if (declarations.any { it.name.value == name }) return true
            }
            return false
        }

        private fun JKClass.hasMemberNamed(name: String, companion: JKClass): Boolean {
            val capitalizedName = name.replaceFirstChar { it.uppercaseChar() }
            val names = listOf(name, "get$capitalizedName", "is$capitalizedName")
            if (classBody.declarations.any { it !== companion && it.name.value in names }) return true
            for (superType in inheritance.extends + inheritance.implements) {
                val psiClass = ((superType.type as? JKClassType)?.classReference as? JKMultiverseClassSymbol)?.target ?: return true
                if (names.any { psiClass.findMethodsByName(it, true).isNotEmpty() }) return true
                if (psiClass.findFieldByName(name, true) != null || psiClass.findInnerClassByName(name, true) != null) return true
            }
            return false
        }

        private val JKSymbol.isTopLevelBuiltInKotlinFunction: Boolean
            get() = this is JKMultiverseFunctionSymbol && fqName.isKotlinPackagePrefix && target.parent is KtFile

        private val String.isKotlinPackagePrefix: Boolean
            get() = this == "kotlin" || this.startsWith("kotlin.")

        private fun JKSymbol.isFromJavaLangPackage(): Boolean =
            fqName.startsWith(JAVA_LANG_FQ_PREFIX)

        private fun JKTreeElement.isSelectorOfQualifiedExpression(): Boolean =
            parent?.safeAs<JKQualifiedExpression>()?.selector == this
    }
}

private class CanBeShortenedCache(private val project: Project) {
    private val shortNameCache = PsiShortNamesCache.getInstance(project)
    private val searchScope = GlobalSearchScope.allScope(project)
    private val hasNoOtherClassifierCache = mutableMapOf<String, Boolean>()
    private val hasNoKotlinExtensionCache = mutableMapOf<String, Boolean>()
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

    /**
     * Ignores the classes with the same qualified name from other libraries, and the class with the same name in `java.lang`.
     * The default import of `java.lang` has a lower priority than the default imports of Kotlin.
     */
    fun hasNoOtherClassifier(symbol: JKClassSymbol): Boolean = hasNoOtherClassifierCache.getOrPut(symbol.fqName) {
        val name = symbol.name
        val ignoredNames = setOf(symbol.fqName, "java.lang.$name")
        var hasOtherClass = false
        shortNameCache.processClassesWithName(name, { psiClass ->
            hasOtherClass = psiClass.qualifiedName !in ignoredNames
            !hasOtherClass
        }, searchScope, null)
        !hasOtherClass && !KotlinTypeAliasShortNameIndex.hasValue(name, project, searchScope)
    }

    fun hasNoKotlinExtension(name: String): Boolean = hasNoKotlinExtensionCache.getOrPut(name) {
        KotlinFunctionShortNameIndex.processElements(name, project, searchScope) { it.receiverTypeReference == null } &&
                KotlinPropertyShortNameIndex.processElements(name, project, searchScope) {
                    (it as? KtCallableDeclaration)?.receiverTypeReference == null
                }
    }

    companion object {
        private val CLASS_NAMES_WHICH_HAVE_DIFFERENT_MEANINGS_IN_KOTLIN_AND_JAVA = setOf(
            "Function",
            "Serializable"
        )
    }
}
