// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KtAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtIntersectionType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.Parameter
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IExtractionData
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IParameter
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.OutputValue
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.TypeDescriptor
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.TypeParameter
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.collectRelevantConstraints
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.types.Variance

class KotlinTypeDescriptor(private val data: IExtractionData) : TypeDescriptor<KtType> {
    override fun KtType.isMeaningful(): Boolean =
        analyze(data.commonParent) {
            !this@isMeaningful.isEqualTo(builtinTypes.UNIT) && !this@isMeaningful.isEqualTo(builtinTypes.NOTHING)
        }

    override fun KtType.isError(): Boolean {
        return this is KtErrorType
    }

    override val booleanType: KtType = analyze(data.commonParent) { builtinTypes.BOOLEAN }

    override val unitType: KtType = analyze(data.commonParent) { builtinTypes.UNIT }
    override val nothingType: KtType = analyze(data.commonParent) { builtinTypes.NOTHING }
    override val nullableAnyType: KtType = analyze(data.commonParent) { builtinTypes.NULLABLE_ANY }

    override fun createListType(argTypes: List<KtType>): KtType {
        return analyze(data.commonParent) {
            buildClassType(StandardClassIds.List) {
                argument(commonSuperType(argTypes) ?: builtinTypes.NULLABLE_ANY)
            }
        }
    }

    override fun createTuple(outputValues: List<OutputValue<KtType>>): KtType {
        analyze(data.commonParent) {
            val boxingClass = when (outputValues.size) {
                1 -> return outputValues.first().valueType
                2 -> getClassOrObjectSymbolByClassId(ClassId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier("Pair")))!!
                3 -> getClassOrObjectSymbolByClassId(ClassId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier("Triple")))!!
                else -> return builtinTypes.UNIT
            }
            return buildClassType(boxingClass) {
                boxingClass.typeParameters.forEachIndexed { idx, s ->
                    argument(outputValues[idx].valueType)
                }
            }
        }
    }

    override fun returnType(ktNamedDeclaration: KtNamedDeclaration): KtType? =
        analyze(data.commonParent) { ktNamedDeclaration.getReturnKtType() }

    override fun renderForMessage(ktNamedDeclaration: KtNamedDeclaration): String {
        return analyze(data.commonParent) {
            ktNamedDeclaration.getSymbol().render(KtDeclarationRendererForSource.WITH_SHORT_NAMES)
        }
    }

    override fun renderForMessage(param: IParameter<KtType>): String {
        val descriptor = (param as Parameter).originalDescriptor
        return if (descriptor is KtNamedDeclaration) {
            renderForMessage(descriptor)
        } else descriptor.name ?: descriptor.text

    }

    override fun renderTypeWithoutApproximation(kotlinType: KtType): String {
        return analyze(data.commonParent) {
            kotlinType.render(position = Variance.INVARIANT)
        }
    }

    override fun typeArguments(ktType: KtType): List<KtType> {
        analyze(data.commonParent) {
            return (ktType as? KtNonErrorClassType)?.ownTypeArguments?.mapNotNull { it.type } ?: emptyList()
        }
    }

    override fun renderType(
        ktType: KtType, isReceiver: Boolean, variance: Variance
    ): String = analyze(data.commonParent) {
        val renderType = ktType.render(position = variance)
        if (ktType.isFunctionType && isReceiver) "($renderType)" else renderType
    }

    override fun isResolvableInScope(
        typeToCheck: KtType,
        typeParameters: MutableSet<TypeParameter>,
    ): Boolean {
        val ktElement = data.targetSibling as KtElement
        return analyze(ktElement) {
            isResolvableInScope(typeToCheck, ktElement, typeParameters)
        }
    }
}

/**
 * Checks whether a given type is resolvable within a scope.
 *
 * @return true if [typeToCheck] doesn't contain unresolved components in the scope of [scope] and is "denotable"
 */
context(KtAnalysisSession)
fun isResolvableInScope(typeToCheck: KtType, scope: KtElement, typeParameters: MutableSet<TypeParameter>): Boolean {
    ((typeToCheck as? KtTypeParameterType)?.symbol?.psi as? KtTypeParameter)?.let { typeParameter ->
        val typeParameterListOwner = typeParameter.parentOfType<KtTypeParameterListOwner>()
        if (typeParameterListOwner == null || !PsiTreeUtil.isAncestor(typeParameterListOwner, scope, true)) {
            typeParameters.add(TypeParameter(typeParameter, typeParameter.collectRelevantConstraints()))
        }
        return true
    }
    if (typeToCheck is KtNonErrorClassType) {

        val classSymbol = typeToCheck.classSymbol
        if ((classSymbol as? KtAnonymousObjectSymbol)?.superTypes?.all { isResolvableInScope(it, scope, typeParameters) } == true) {
            return true
        }

        if ((classSymbol as? KtClassOrObjectSymbol)?.classIdIfNonLocal == null) {
            //because org.jetbrains.kotlin.fir.FirVisibilityChecker.Default always return true for local classes,
            //let's be pessimistic here and prohibit local classes completely
            return false
        }

        if (classSymbol is KtSymbolWithVisibility && !isVisible(classSymbol, scope.containingKtFile.getFileSymbol(), null, scope)) {
            return false
        }
        typeToCheck.ownTypeArguments.mapNotNull { it.type }.forEach {
            if (!isResolvableInScope(it, scope, typeParameters)) return false
        }
    }
    if (typeToCheck is KtErrorType) {
        return false
    }
    if (typeToCheck is KtIntersectionType) {
        return false
    }
    return true
}


context(KtAnalysisSession)
fun approximateWithResolvableType(type: KtType?, scope: KtElement): KtType? {
    if (type == null) return null
    if (!(type is KtNonErrorClassType && type.classSymbol is KtAnonymousObjectSymbol)
        && isResolvableInScope(type, scope, mutableSetOf())
    ) return type
    return type.getAllSuperTypes().firstOrNull {
        isResolvableInScope(it, scope, mutableSetOf())
    }
}