// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightRecordMethod
import com.intellij.psi.util.JavaPsiRecordUtil.getFieldForComponent
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.asJava.elements.KtLightDeclaration
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.nj2k.symbols.*
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.JKTypeFactory
import org.jetbrains.kotlin.psi.*

class JKSymbolProvider(private val resolver: JKResolver) {
    lateinit var typeFactory: JKTypeFactory

    val symbolsByPsi: MutableMap<PsiElement, JKSymbol> = mutableMapOf()

    private val symbolsByFqName: MutableMap<String, JKSymbol> = mutableMapOf()
    private val symbolsByFqNameWithExactSignature: MutableMap<List<String>, JKSymbol> = mutableMapOf()
    private val symbolsByJK: MutableMap<JKDeclaration, JKSymbol> = mutableMapOf()

    fun preBuildTree(inputElements: List<PsiElement>) {
        val visitor = SymbolProviderVisitor(symbolProvider = this)
        for (element in inputElements) {
            element.accept(visitor)
        }
    }

    fun provideDirectSymbol(psi: PsiElement): JKSymbol {
        return symbolsByPsi.getOrPut(psi) {
            if (psi is KtLightDeclaration<*, *>) {
                psi.kotlinOrigin?.let { provideDirectSymbol(it) } ?: createDirectSymbol(psi)
            } else {
                createDirectSymbol(psi)
            }
        }
    }

    context(_: KaSession)
    fun provideDirectSymbol(symbol: KaDeclarationSymbol): JKSymbol {
        val psi = symbol.psi ?: return JKUnresolvedClassSymbol(NO_NAME_PROVIDED, typeFactory)
        return provideDirectSymbol(psi)
    }

    internal inline fun <reified T : JKSymbol> provideSymbolForReference(reference: PsiReference): T {
        val target = reference.resolve()
        if (target is LightRecordMethod) {
            val field = getFieldForComponent(target.recordComponent)
            if (field != null) return provideDirectSymbol(field) as T
        }
        if (target != null) return provideDirectSymbol(target) as T
        val unresolvedSymbol =
            if (isAssignable<T, JKUnresolvedField>()) JKUnresolvedField(reference.canonicalText, typeFactory)
            else JKUnresolvedMethod(reference, typeFactory)
        return unresolvedSymbol as T
    }

    fun transferSymbol(to: JKDeclaration, from: JKDeclaration): JKSymbol? =
        symbolsByJK[from]?.also {
            @Suppress("UNCHECKED_CAST")
            it as JKUniverseSymbol<JKDeclaration>
            it.target = to
            symbolsByJK[to] = it
        }

    fun provideUniverseSymbol(psi: PsiElement, declaration: JKDeclaration): JKSymbol {
        val symbol = provideUniverseSymbol(psi)
        when (symbol) {
            is JKUniverseClassSymbol -> symbol.target = declaration as JKClass
            is JKUniverseFieldSymbol -> symbol.target = declaration as JKVariable
            is JKUniverseMethodSymbol -> symbol.target = declaration as JKMethod
            is JKUniverseTypeParameterSymbol -> symbol.target = declaration as JKTypeParameter
            is JKUniversePackageSymbol -> symbol.target = declaration as JKPackageDeclaration
        }
        symbolsByJK[declaration] = symbol
        return symbol
    }

    fun provideUniverseSymbol(psi: PsiElement): JKSymbol =
        symbolsByPsi.getOrPut(psi) {
            when (psi) {
                is PsiVariable -> JKUniverseFieldSymbol(typeFactory)
                is PsiMethod -> JKUniverseMethodSymbol(typeFactory)
                is PsiTypeParameter -> JKUniverseTypeParameterSymbol(typeFactory)
                is PsiClass -> JKUniverseClassSymbol(typeFactory)
                is PsiPackageStatement -> JKUniversePackageSymbol(typeFactory)
                else -> error("Unexpected argument type: ${psi::class}")
            }
        }

    fun provideUniverseSymbol(jk: JKClass): JKClassSymbol = symbolsByJK.getOrPut(jk) {
        JKUniverseClassSymbol(typeFactory).also { it.target = jk }
    } as JKClassSymbol

    fun provideUniverseSymbol(jk: JKVariable): JKFieldSymbol = symbolsByJK.getOrPut(jk) {
        JKUniverseFieldSymbol(typeFactory).also { it.target = jk }
    } as JKFieldSymbol

    fun provideUniverseSymbol(jk: JKMethod): JKMethodSymbol = symbolsByJK.getOrPut(jk) {
        JKUniverseMethodSymbol(typeFactory).also { it.target = jk }
    } as JKMethodSymbol

    fun provideUniverseSymbol(jk: JKTypeParameter): JKTypeParameterSymbol = symbolsByJK.getOrPut(jk) {
        JKUniverseTypeParameterSymbol(typeFactory).also { it.target = jk }
    } as JKTypeParameterSymbol

    fun provideUniverseSymbol(jk: JKPackageDeclaration): JKPackageSymbol = symbolsByJK.getOrPut(jk) {
        JKUniversePackageSymbol(typeFactory).also { it.target = jk }
    } as JKPackageSymbol

    fun provideUniverseSymbol(jk: JKDeclaration): JKUniverseSymbol<*>? = when (jk) {
        is JKClass -> provideUniverseSymbol(jk)
        is JKVariable -> provideUniverseSymbol(jk)
        is JKMethod -> provideUniverseSymbol(jk)
        is JKTypeParameter -> provideUniverseSymbol(jk)
        else -> null
    } as? JKUniverseSymbol<*>

    fun provideClassSymbol(fqName: FqName): JKClassSymbol =
        symbolsByFqName.getOrPutIfNotNull(fqName.asString()) {
            resolver.resolveClass(fqName)?.let {
                provideDirectSymbol(it) as? JKClassSymbol
            }
        } as? JKClassSymbol ?: JKUnresolvedClassSymbol(fqName.asString(), typeFactory)

    fun provideClassSymbol(fqName: String): JKClassSymbol =
        provideClassSymbol(fqName.asSafeFqName())

    fun provideClassSymbol(fqName: FqNameUnsafe): JKClassSymbol =
        provideClassSymbol(fqName.toSafe())

    fun provideMethodSymbol(fqName: FqName): JKMethodSymbol =
        symbolsByFqName.getOrPutIfNotNull(fqName.asString()) {
            resolver.resolveMethod(fqName)?.let {
                provideDirectSymbol(it) as? JKMethodSymbol
            }
        } as? JKMethodSymbol ?: JKUnresolvedMethod(fqName.asString(), typeFactory)

    fun provideMethodSymbol(fqName: String): JKMethodSymbol =
        provideMethodSymbol(fqName.asSafeFqName())

    context(_: KaSession)
    fun provideMethodSymbolWithExactSignature(methodFqName: String, parameterTypesFqNames: List<String>): JKMethodSymbol {
        return symbolsByFqNameWithExactSignature.getOrPutIfNotNull(listOf(methodFqName) + parameterTypesFqNames) {
            resolver.resolveMethodWithExactSignature(
                methodFqName.asSafeFqName(),
                parameterTypesFqNames.map { it.asSafeFqName() }
            )?.let {
                provideDirectSymbol(it) as? JKMethodSymbol
            }
        } as? JKMethodSymbol ?: JKUnresolvedMethod(methodFqName, typeFactory)
    }

    fun provideFieldSymbol(fqName: String): JKFieldSymbol =
        provideFieldSymbol(fqName.asSafeFqName())

    private fun createDirectSymbol(psi: PsiElement): JKSymbol = when (psi) {
        is PsiTypeParameter -> JKMultiverseTypeParameterSymbol(psi, typeFactory)
        is KtTypeParameter -> JKMultiverseKtTypeParameterSymbol(psi, typeFactory)
        is KtEnumEntry -> JKMultiverseKtEnumEntrySymbol(psi, typeFactory)
        is PsiClass -> JKMultiverseClassSymbol(psi, typeFactory)
        is KtClassOrObject -> JKMultiverseKtClassSymbol(psi, typeFactory)
        is PsiMethod -> JKMultiverseMethodSymbol(psi, typeFactory)
        is PsiField -> JKMultiverseFieldSymbol(psi, typeFactory)
        is KtFunction -> JKMultiverseFunctionSymbol(psi, typeFactory)
        is KtProperty -> JKMultiversePropertySymbol(psi, typeFactory)
        is KtParameter -> JKMultiversePropertySymbol(psi, typeFactory)
        is PsiParameter -> JKMultiverseFieldSymbol(psi, typeFactory)
        is PsiLocalVariable -> JKMultiverseFieldSymbol(psi, typeFactory)
        is PsiPackage -> JKMultiversePackageSymbol(psi, typeFactory)
        is KtTypeAlias -> JKTypeAliasKtClassSymbol(psi, typeFactory)
        else -> error("Unexpected argument type: ${psi::class}")
    }

    private fun provideFieldSymbol(fqName: FqName): JKFieldSymbol =
        symbolsByFqName.getOrPutIfNotNull(fqName.asString()) {
            resolver.resolveField(fqName)?.let {
                provideDirectSymbol(it) as? JKFieldSymbol
            }
        } as? JKFieldSymbol ?: JKUnresolvedField(fqName.asString(), typeFactory)

    private inline fun <reified A, reified B> isAssignable(): Boolean = A::class.java.isAssignableFrom(B::class.java)

    private inline fun <K, V : Any> MutableMap<K, V>.getOrPutIfNotNull(key: K, defaultValue: () -> V?): V? {
        val value = get(key)
        return if (value == null) {
            val answer = defaultValue() ?: return null
            put(key, answer)
            answer
        } else {
            value
        }
    }

    private fun String.asSafeFqName(): FqName =
        FqName(replace('/', '.'))

    private val NO_NAME_PROVIDED: String = "NO_NAME_PROVIDED"
}

private class SymbolProviderVisitor(private val symbolProvider: JKSymbolProvider) : JavaElementVisitor(),PsiRecursiveVisitor {
    override fun visitElement(element: PsiElement) {
        element.acceptChildren(this)
    }

    override fun visitClass(aClass: PsiClass) {
        symbolProvider.provideUniverseSymbol(aClass)
        aClass.acceptChildren(this)
    }

    override fun visitField(field: PsiField) {
        symbolProvider.provideUniverseSymbol(field)
    }

    override fun visitParameter(parameter: PsiParameter) {
        symbolProvider.provideUniverseSymbol(parameter)
    }

    override fun visitMethod(method: PsiMethod) {
        symbolProvider.provideUniverseSymbol(method)
        method.acceptChildren(this)
    }

    override fun visitEnumConstant(enumConstant: PsiEnumConstant) {
        symbolProvider.provideUniverseSymbol(enumConstant)
        enumConstant.acceptChildren(this)
    }

    override fun visitTypeParameter(classParameter: PsiTypeParameter) {
        symbolProvider.provideUniverseSymbol(classParameter)
    }

    override fun visitPackageStatement(statement: PsiPackageStatement) {
        symbolProvider.provideUniverseSymbol(statement)
    }

    override fun visitFile(file: PsiFile) {
        file.acceptChildren(this)
    }
}
