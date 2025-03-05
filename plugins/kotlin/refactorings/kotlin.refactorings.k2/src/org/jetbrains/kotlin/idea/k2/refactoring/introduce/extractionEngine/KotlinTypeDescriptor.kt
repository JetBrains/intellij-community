// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.Parameter
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.types.Variance

class KotlinTypeDescriptor(private val data: IExtractionData) : TypeDescriptor<KaType> {
    override fun KaType.isMeaningful(): Boolean =
        analyze(data.commonParent) {
            !this@isMeaningful.semanticallyEquals(builtinTypes.unit) && !this@isMeaningful.semanticallyEquals(builtinTypes.nothing)
        }

    override fun KaType.isError(): Boolean {
        return this is KaErrorType
    }

    override val booleanType: KaType = analyze(data.commonParent) { builtinTypes.boolean }

    override val unitType: KaType = analyze(data.commonParent) { builtinTypes.unit }
    override val nothingType: KaType = analyze(data.commonParent) { builtinTypes.nothing }
    override val nullableAnyType: KaType = analyze(data.commonParent) { builtinTypes.nullableAny }

    override fun createListType(argTypes: List<KaType>): KaType {
        return analyze(data.commonParent) {
            buildClassType(StandardClassIds.List) {
                val commonSupertype = if (argTypes.isNotEmpty()) argTypes.commonSupertype else builtinTypes.nullableAny
                argument(commonSupertype)
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    override fun createTuple(outputValues: List<OutputValue<KaType>>): KaType {
        analyze(data.commonParent) {
            val boxingClass = when (outputValues.size) {
                1 -> return outputValues.first().valueType
                2 -> findClass(ClassId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier("Pair")))!!
                3 -> findClass(ClassId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier("Triple")))!!
                else -> return builtinTypes.unit
            }
            return buildClassType(boxingClass) {
                boxingClass.typeParameters.forEachIndexed { idx, s ->
                    argument(outputValues[idx].valueType)
                }
            }
        }
    }

    override fun returnType(ktNamedDeclaration: KtNamedDeclaration): KaType? =
        analyze(data.commonParent) { ktNamedDeclaration.returnType }

    @OptIn(KaExperimentalApi::class)
    override fun renderForMessage(ktNamedDeclaration: KtNamedDeclaration): String {
        return analyze(data.commonParent) {
            ktNamedDeclaration.symbol.render(KaDeclarationRendererForSource.WITH_SHORT_NAMES)
        }
    }

    override fun renderForMessage(param: IParameter<KaType>): String {
        val descriptor = (param as Parameter).originalDescriptor
        return if (descriptor is KtNamedDeclaration) {
            renderForMessage(descriptor)
        } else descriptor.name ?: descriptor.text

    }

    @OptIn(KaExperimentalApi::class)
    override fun renderTypeWithoutApproximation(kotlinType: KaType): String {
        return analyze(data.commonParent) {
            kotlinType.render(position = Variance.INVARIANT)
        }
    }

    override fun typeArguments(ktType: KaType): List<KaType> {
        analyze(data.commonParent) {
            return (ktType as? KaClassType)?.typeArguments?.mapNotNull { it.type } ?: emptyList()
        }
    }

    @OptIn(KaExperimentalApi::class)
    override fun renderType(
        ktType: KaType, isReceiver: Boolean, variance: Variance
    ): String = analyze(data.commonParent) {
        val renderType = ktType.render(position = variance)
        if (ktType.isFunctionType && isReceiver) "($renderType)" else renderType
    }

    override fun isResolvableInScope(
        typeToCheck: KaType,
        typeParameters: MutableSet<TypeParameter>,
    ): Boolean {
        return analyze(data.commonParent) {
            isResolvableInScope(typeToCheck, data.targetSibling, typeParameters)
        }
    }
}

/**
 * Checks whether a given type is resolvable within a scope.
 *
 * @return true if [typeToCheck] doesn't contain unresolved components in the scope of [scope] and is "denotable"
 */
context(KaSession)
@OptIn(KaExperimentalApi::class)
fun isResolvableInScope(typeToCheck: KaType, scope: PsiElement, typeParameters: MutableSet<TypeParameter>): Boolean {
    require(scope.containingFile is KtFile)
    ((typeToCheck as? KaTypeParameterType)?.symbol?.psi as? KtTypeParameter)?.let { typeParameter ->
        val typeParameterListOwner = typeParameter.parentOfType<KtTypeParameterListOwner>()
        if (typeParameterListOwner == null || !PsiTreeUtil.isAncestor(typeParameterListOwner, scope, true)) {
            typeParameters.add(TypeParameter(typeParameter, typeParameter.collectRelevantConstraints()))
        }
        return true
    }
    if (typeToCheck is KaClassType) {

        val classSymbol = typeToCheck.symbol
        if ((classSymbol as? KaAnonymousObjectSymbol)?.superTypes?.all { isResolvableInScope(it, scope, typeParameters) } == true) {
            return true
        }

        if ((classSymbol as? KaClassSymbol)?.classId == null) {
            //because org.jetbrains.kotlin.fir.FirVisibilityChecker.Default always return true for local classes,
            //let's be pessimistic here and prohibit local classes completely
            return false
        }

        val fileSymbol = (scope.containingFile as KtFile).symbol
        if (!createUseSiteVisibilityChecker(fileSymbol, receiverExpression = null, scope).isVisible(classSymbol)) {
            return false
        }

        typeToCheck.typeArguments.mapNotNull { it.type }.forEach {
            if (!isResolvableInScope(it, scope, typeParameters)) return false
        }
    }
    if (typeToCheck is KaErrorType) {
        return false
    }
    if (typeToCheck is KaIntersectionType) {
        return false
    }
    if (typeToCheck is KaDefinitelyNotNullType) {
        if (!isResolvableInScope(typeToCheck.original, scope, typeParameters)) return false
    }
    return true
}


context(KaSession)
fun approximateWithResolvableType(type: KaType?, scope: PsiElement): KaType? {
    if (type == null) return null
    if (!(type is KaClassType && type.symbol is KaAnonymousObjectSymbol)
        && isResolvableInScope(type, scope, mutableSetOf())
    ) return type
    return type.allSupertypes.firstOrNull {
        isResolvableInScope(it, scope, mutableSetOf())
    }
}