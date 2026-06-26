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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.impl.hotswap.SourceFileChangeCompatibilityChecker
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclarationContainer
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtObjectLiteralExpression
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
        val fileClass = ktFile.fileClassSnapshot()
        val declaredClasses = ktFile.declarations.filterIsInstance<KtClassOrObject>().flatMap { it.snapshot() }
        return (fileClass + declaredClasses).associateByTo(linkedMapOf()) { it.name }
    }

    private fun KtFile.fileClassSnapshot(): List<HotSwapClassShape> {
        val className = buildFileClassName()
        val innerClasses = sourceAnonymousClasses()
        val shape = HotSwapClassShape(
            className,
            "file",
            emptySet(),
            emptySet(),
            innerClasses.mapTo(hashSetOf()) { "$className.${it.name}" },
            sourceFields().associatePropertyShapes(),
            sourceMethods().associateFunctionShapes() +
                    sourceLambdas().mapIndexed { index, lambda -> lambda.snapshot(index) },
        )
        return listOf(shape) + innerClasses.flatMap { it.classOrObject.snapshot("$className.", it.name, it.isAnonymous) }
    }

    private fun KtFile.buildFileClassName(): String {
        val fileClassName = name.removeSuffix(".kt").removeSuffix(".kts") + "Kt"
        val packageName = packageFqName.asString()
        return if (packageName.isEmpty()) fileClassName else "$packageName.$fileClassName"
    }

    private fun KtClassOrObject.snapshot(
        prefix: String = "",
        syntheticName: String? = null,
        anonymous: Boolean = false
    ): List<HotSwapClassShape> {
        val declaredName = syntheticName ?: className()
        val className = prefix + declaredName
        val innerClasses = sourceInnerClasses()
        val fields = sourceFields().associatePropertyShapes() +
                sourceConstructorFields().associateParameterPropertyShapes() +
                capturedFields(anonymous)
        val methods = sourceMethods().associateFunctionShapes() +
                sourceConstructors().associateConstructorShapes() +
                sourceLambdas().mapIndexed { index, lambda -> lambda.snapshot(index) }
        val shape = HotSwapClassShape(
            className,
            kind(anonymous),
            modifiers(CLASS_MODIFIERS),
            superTypeListEntries.mapTo(hashSetOf()) {
                it.resolvedTypeSignature(it.typeReference, "Kotlin supertype '${it.text.filterNot { char -> char.isWhitespace() }}'")
            },
            innerClasses.mapTo(hashSetOf()) { "$className.${it.name}" },
            fields,
            methods,
        )
        return listOf(shape) + innerClasses.flatMap { it.classOrObject.snapshot("$className.", it.name, it.isAnonymous) }
    }

    private fun KtClassOrObject.className(): String =
        fqName?.asString() ?: name ?: unknownClassShapes("Cannot determine Kotlin class name in ${containingKtFile.name}")

    private fun KtDeclarationContainer.sourceFields(): List<KtProperty> = declarations.filterIsInstance<KtProperty>()

    private fun KtDeclarationContainer.sourceMethods(): List<KtNamedFunction> = declarations.filterIsInstance<KtNamedFunction>()

    private fun KtClassOrObject.sourceConstructorFields(): List<KtParameter> =
        (this as? KtClass)?.primaryConstructorParameters.orEmpty().filter { it.hasValOrVar() }

    private fun KtClassOrObject.sourceConstructors(): List<KtConstructor<*>> =
        (this as? KtClass)?.let { ktClass ->
            listOfNotNull(ktClass.primaryConstructor) + ktClass.secondaryConstructors
        }.orEmpty()

    private fun KtClassOrObject.sourceInnerClasses(): List<SourceInnerClass> {
        val namedClasses = declarations.filterIsInstance<KtClassOrObject>()
            .map { SourceInnerClass(it, it.className(), false) }
        return namedClasses + sourceAnonymousClasses()
    }

    private fun PsiElement.sourceAnonymousClasses(): List<SourceInnerClass> {
        val enclosingClass = this as? KtClassOrObject
        return PsiTreeUtil.collectElementsOfType(this, KtObjectLiteralExpression::class.java)
            .filter { it.enclosingSourceClass() == enclosingClass }
            .mapIndexed { index, expression -> expression.toAnonymousSourceClass(index) }
    }

    private fun KtObjectLiteralExpression.toAnonymousSourceClass(index: Int): SourceInnerClass =
        SourceInnerClass(objectDeclaration, "anonymous$index", true)

    private fun PsiElement.sourceLambdas(): List<KtLambdaExpression> {
        val enclosingClass = this as? KtClassOrObject
        return PsiTreeUtil.collectElementsOfType(this, KtLambdaExpression::class.java)
            .filter { it.enclosingSourceClass() == enclosingClass }
    }

    private fun PsiElement.enclosingSourceClass(): KtClassOrObject? = PsiTreeUtil.getParentOfType(this, KtClassOrObject::class.java, true)

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

    private fun KtLambdaExpression.snapshot(index: Int): Pair<HotSwapMethodId, HotSwapMethodShape> {
        val (declaredParameters, returnType) = signature()
        val capturedParameters = capturedVariables().map { it.capturedSignature() }
        val id = HotSwapMethodId("lambda$index", false, capturedParameters + declaredParameters)
        return id to HotSwapMethodShape(returnType, emptySet())
    }

    private fun KtClassOrObject.capturedFields(anonymous: Boolean): Map<String, HotSwapFieldShape> {
        if (!anonymous) return emptyMap()
        return capturedVariables().mapIndexed { index, declaration ->
            val type = declaration.resolvedTypeSignature("type for captured Kotlin variable '${declaration.name.orEmpty()}'")
            "capture$index${declaration.name.orEmpty()}" to HotSwapFieldShape(type, emptySet())
        }.toMap()
    }

    private fun KtCallableDeclaration.capturedSignature(): String =
        resolvedTypeSignature("type for captured Kotlin variable '${name.orEmpty()}'")

    private fun KtElement.capturedVariables(): List<KtCallableDeclaration> {
        val declarations = linkedSetOf<KtCallableDeclaration>()
        PsiTreeUtil.processElements(this) { element ->
            if (element is KtNameReferenceExpression) {
                val declaration = element.mainReference.resolve() as? KtCallableDeclaration
                if (declaration != null && !PsiTreeUtil.isAncestor(this, declaration, false) && declaration.isCapturableVariable()) {
                    declarations.add(declaration)
                }
            }
            true
        }
        return declarations.sortedBy { it.textOffset }
    }

    private fun KtCallableDeclaration.isCapturableVariable(): Boolean = when (this) {
        is KtProperty -> isLocal
        is KtParameter -> true
        else -> false
    }

    @OptIn(KaExperimentalApi::class)
    private fun KtLambdaExpression.signature(): Pair<List<String>, String> = analyze(this) {
        val symbol = functionLiteral.symbol
        val parameterTypes = listOfNotNull(symbol.receiverParameter?.returnType)
            .plus(symbol.valueParameters.map { it.returnType })
            .map { it.render(TYPE_RENDERER, position = Variance.INVARIANT) }
        val returnType = symbol.returnType.render(TYPE_RENDERER, position = Variance.INVARIANT)
        parameterTypes to returnType
    }

    private fun KtClassOrObject.kind(anonymous: Boolean = false): String = when {
        anonymous -> "class"
        this is KtObjectDeclaration -> "object"
        this is KtClass -> when {
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

private data class SourceInnerClass(val classOrObject: KtClassOrObject, val name: String, val isAnonymous: Boolean)

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
