// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.hotswap

import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.hotswap.HotSwapClassShape
import com.intellij.debugger.impl.hotswap.HotSwapFieldShape
import com.intellij.debugger.impl.hotswap.HotSwapMethodId
import com.intellij.debugger.impl.hotswap.HotSwapMethodShape
import com.intellij.debugger.impl.hotswap.HotSwapSourceChangeCompatibilityCheckerProvider
import com.intellij.debugger.impl.hotswap.JvmBaseSourceFileChangeCompatibilityChecker
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.xdebugger.impl.hotswap.SourceFileChangeCompatibilityChecker
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtValVarKeywordOwner
import org.jetbrains.kotlin.types.Variance

internal class KotlinHotSwapSourceChangeCompatibilityCheckerProvider : HotSwapSourceChangeCompatibilityCheckerProvider {
    override fun provideCheckersForSession(debuggerSession: DebuggerSession): List<SourceFileChangeCompatibilityChecker> {
        return listOf(KotlinHotSwapSourceChangeCompatibilityChecker(debuggerSession.project))
    }
}

@ApiStatus.Internal
class KotlinHotSwapSourceChangeCompatibilityChecker(project: Project) :
    JvmBaseSourceFileChangeCompatibilityChecker(project, KotlinFileType.INSTANCE) {

    override fun buildClassShapes(file: PsiFile): Map<String, HotSwapClassShape> {
        val ktFile = file as? KtFile ?: unknownClassShapes("Expected KtFile, got ${file::class.java.name}")
        val fileClass = ktFile.fileClassShape()
        val classes = ktFile.declarations.filterIsInstance<KtClassOrObject>().flatMap { it.snapshot() }
        return linkedMapOf(fileClass.name to fileClass).apply {
            putAll(classes.associateBy { it.name })
        }
    }

    private fun KtFile.fileClassShape(): HotSwapClassShape {
        return HotSwapClassShape(
            buildFileClassName(),
            "file",
            emptySet(),
            emptySet(),
            emptySet(),
            declarations.filterIsInstance<KtProperty>().associatePropertyShapes(),
            declarations.filterIsInstance<KtNamedFunction>().associateFunctionShapes(),
        )
    }

    private fun KtFile.buildFileClassName(): String {
        val fileClassName = name.removeSuffix(".kt").removeSuffix(".kts") + "Kt"
        val packageName = packageFqName.asString()
        return if (packageName.isEmpty()) fileClassName else "$packageName.$fileClassName"
    }

    private fun KtClassOrObject.snapshot(prefix: String = ""): List<HotSwapClassShape> {
        val declaredName = fqName?.asString()
            ?: name ?: unknownClassShapes("Cannot determine Kotlin class name in ${containingKtFile.name}")
        val className = prefix + declaredName
        val declaredProperties = declarations.filterIsInstance<KtProperty>()
        val constructorProperties = (this as? KtClass)?.primaryConstructorParameters.orEmpty().filter { it.hasValOrVar() }
        val fields = declaredProperties.associatePropertyShapes() + constructorProperties.associateParameterPropertyShapes()
        val methods = declarations.filterIsInstance<KtNamedFunction>().associateFunctionShapes()
        val constructors = (this as? KtClass)?.let { ktClass ->
            (listOfNotNull(ktClass.primaryConstructor) + ktClass.secondaryConstructors).associateConstructorShapes()
        }.orEmpty()
        val nestedClasses = declarations.filterIsInstance<KtClassOrObject>()
            .flatMap { it.snapshot("$className.") }
            .associateBy { it.name }
        val shape = HotSwapClassShape(
            className,
            kind(),
            modifiers(CLASS_MODIFIERS),
            superTypeListEntries.mapTo(hashSetOf()) {
                it.resolvedTypeSignature(it.typeReference, "Kotlin supertype '${it.text.filterNot { char -> char.isWhitespace() }}'")
            },
            nestedClasses.keys,
            fields,
            methods + constructors,
        )
        return nestedClasses.values + shape
    }

    private fun List<KtProperty>.associatePropertyShapes(): Map<String, HotSwapFieldShape> {
        val result = linkedMapOf<String, HotSwapFieldShape>()
        for (property in this) {
            val name = property.name ?: unknownClassShapes("Cannot determine Kotlin property name")
            val type = property.resolvedTypeSignature("type for Kotlin property '$name'")
            result[name] = HotSwapFieldShape(type, property.fieldModifiers())
        }
        return result
    }

    private fun List<KtParameter>.associateParameterPropertyShapes(): Map<String, HotSwapFieldShape> {
        val result = linkedMapOf<String, HotSwapFieldShape>()
        for (parameter in this) {
            val name = parameter.name ?: unknownClassShapes("Cannot determine Kotlin parameter property name")
            val type = parameter.resolvedTypeSignature("type for Kotlin parameter property '$name'")
            result[name] = HotSwapFieldShape(type, parameter.fieldModifiers())
        }
        return result
    }

    private fun List<KtNamedFunction>.associateFunctionShapes(): Map<HotSwapMethodId, HotSwapMethodShape> {
        val result = linkedMapOf<HotSwapMethodId, HotSwapMethodShape>()
        for (function in this) {
            val name = function.name ?: unknownClassShapes("Cannot determine Kotlin function name")
            val parameterTypes = function.valueParameters.map {
                it.resolvedTypeSignature("type for Kotlin function parameter '${it.name.orEmpty()}'")
            }
            val returnType = function.resolvedTypeSignature("return type for Kotlin function '$name'")
            val id = HotSwapMethodId(name, false, parameterTypes)
            result[id] = HotSwapMethodShape(returnType, function.modifiers(METHOD_MODIFIERS))
        }
        return result
    }

    private fun List<KtConstructor<*>>.associateConstructorShapes(): Map<HotSwapMethodId, HotSwapMethodShape> {
        val result = linkedMapOf<HotSwapMethodId, HotSwapMethodShape>()
        for (constructor in this) {
            val parameterTypes = constructor.valueParameters.map {
                it.resolvedTypeSignature("type for Kotlin constructor parameter '${it.name.orEmpty()}'")
            }
            result[HotSwapMethodId("<init>", true, parameterTypes)] = HotSwapMethodShape(null, constructor.modifiers(METHOD_MODIFIERS))
        }
        return result
    }

    private fun KtClassOrObject.kind(): String = when (this) {
        is KtObjectDeclaration -> "object"
        is KtClass -> when {
            isAnnotation() -> "annotation"
            isEnum() -> "enum"
            isInterface() -> "interface"
            else -> "class"
        }

        else -> "class"
    }

    private fun KtModifierListOwner.modifiers(knownModifiers: Array<KtModifierKeywordToken>): Set<String> =
        knownModifiers.filterTo(hashSetOf()) { hasModifier(it) }.mapTo(hashSetOf()) { it.value }

    private fun KtValVarKeywordOwner.fieldModifiers(): Set<String> =
        (this as KtModifierListOwner).modifiers(FIELD_MODIFIERS) + if (isMutableProperty()) "var" else "val"

    private fun KtValVarKeywordOwner.isMutableProperty(): Boolean = valOrVarKeyword?.node?.elementType == KtTokens.VAR_KEYWORD

    @OptIn(KaExperimentalApi::class)
    private fun KtCallableDeclaration.resolvedTypeSignature(typeDescription: String): String = analyze(this) {
        (symbol as? KaCallableSymbol)?.returnType?.render(TYPE_RENDERER, position = Variance.INVARIANT)
    } ?: unknownClassShapes("Cannot resolve $typeDescription")

    @OptIn(KaExperimentalApi::class)
    private fun KtElement.resolvedTypeSignature(typeReference: KtTypeReference?, typeDescription: String): String = analyze(this) {
        typeReference?.type?.render(TYPE_RENDERER, position = Variance.INVARIANT)
    } ?: unknownClassShapes("Cannot resolve $typeDescription")
}

@OptIn(KaExperimentalApi::class)
private val TYPE_RENDERER = KaTypeRendererForSource.WITH_QUALIFIED_NAMES_WITHOUT_PARAMETER_NAMES

private val CLASS_MODIFIERS = arrayOf(
    KtTokens.PUBLIC_KEYWORD,
    KtTokens.PROTECTED_KEYWORD,
    KtTokens.PRIVATE_KEYWORD,
    KtTokens.INTERNAL_KEYWORD,
    KtTokens.FINAL_KEYWORD,
    KtTokens.OPEN_KEYWORD,
    KtTokens.ABSTRACT_KEYWORD,
    KtTokens.SEALED_KEYWORD,
    KtTokens.DATA_KEYWORD,
    KtTokens.VALUE_KEYWORD,
    KtTokens.INNER_KEYWORD,
)
private val FIELD_MODIFIERS = arrayOf(
    KtTokens.PUBLIC_KEYWORD,
    KtTokens.PROTECTED_KEYWORD,
    KtTokens.PRIVATE_KEYWORD,
    KtTokens.INTERNAL_KEYWORD,
    KtTokens.FINAL_KEYWORD,
    KtTokens.OPEN_KEYWORD,
    KtTokens.CONST_KEYWORD,
    KtTokens.LATEINIT_KEYWORD,
)
private val METHOD_MODIFIERS = arrayOf(
    KtTokens.PUBLIC_KEYWORD,
    KtTokens.PROTECTED_KEYWORD,
    KtTokens.PRIVATE_KEYWORD,
    KtTokens.INTERNAL_KEYWORD,
    KtTokens.FINAL_KEYWORD,
    KtTokens.OPEN_KEYWORD,
    KtTokens.ABSTRACT_KEYWORD,
    KtTokens.INLINE_KEYWORD,
    KtTokens.OPERATOR_KEYWORD,
    KtTokens.INFIX_KEYWORD,
    KtTokens.SUSPEND_KEYWORD,
    KtTokens.TAILREC_KEYWORD,
)
