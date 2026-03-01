// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeVisitor
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.impl.source.PsiClassReferenceType
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.j2k.ast.ArrayType
import org.jetbrains.kotlin.j2k.ast.ClassType
import org.jetbrains.kotlin.j2k.ast.ErrorType
import org.jetbrains.kotlin.j2k.ast.Identifier
import org.jetbrains.kotlin.j2k.ast.InProjectionType
import org.jetbrains.kotlin.j2k.ast.Mutability
import org.jetbrains.kotlin.j2k.ast.NullType
import org.jetbrains.kotlin.j2k.ast.OutProjectionType
import org.jetbrains.kotlin.j2k.ast.PrimitiveType
import org.jetbrains.kotlin.j2k.ast.ReferenceElement
import org.jetbrains.kotlin.j2k.ast.StarProjectionType
import org.jetbrains.kotlin.j2k.ast.Type
import org.jetbrains.kotlin.j2k.ast.UnitType
import org.jetbrains.kotlin.j2k.ast.VarArgType
import org.jetbrains.kotlin.j2k.ast.assignNoPrototype
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.jvm.JvmPrimitiveType

private val PRIMITIVE_TYPES_NAMES = JvmPrimitiveType.values().map { it.javaKeywordName }

@K1Deprecation
class TypeVisitor(
        private val converter: Converter,
        private val topLevelType: PsiType,
        private val topLevelTypeMutability: Mutability,
        private val inAnnotationType: Boolean
) : PsiTypeVisitor<Type>() {

    private val typeConverter: TypeConverter = converter.typeConverter

    //TODO: support for all types
    override fun visitType(type: PsiType) = ErrorType()

    override fun visitPrimitiveType(primitiveType: PsiPrimitiveType): Type {
        val name = primitiveType.canonicalText
        return when {
            name == "void" -> UnitType()
            PRIMITIVE_TYPES_NAMES.contains(name) -> PrimitiveType(Identifier.withNoPrototype(StringUtil.capitalize(name)))
            name == "null" -> NullType()
            else -> PrimitiveType(Identifier.withNoPrototype(name))
        }
    }

    override fun visitArrayType(arrayType: PsiArrayType): Type {
        return ArrayType(typeConverter.convertType(arrayType.componentType, inAnnotationType = inAnnotationType), Nullability.Default, converter.settings)
    }

    override fun visitClassType(classType: PsiClassType): Type {
        val mutability = if (classType === topLevelType) topLevelTypeMutability else Mutability.Default
        val refElement = constructReferenceElement(classType, mutability)
        return ClassType(refElement, Nullability.Default, converter.settings)
    }

    private fun constructReferenceElement(classType: PsiClassType, mutability: Mutability): ReferenceElement {
        val typeArgs = convertTypeArgs(classType)

        val psiClass = classType.resolve()
        if (psiClass != null) {
            val javaClassName = psiClass.qualifiedName
            converter.convertToKotlinAnalogIdentifier(javaClassName, mutability)?.let {
                return ReferenceElement(it, typeArgs).assignNoPrototype()
            }

            if (inAnnotationType && javaClassName == "java.lang.Class") {
                val fqName = FqName("kotlin.reflect.KClass")
                val identifier = Identifier.withNoPrototype(fqName.shortName().identifier, imports = listOf(fqName))
                return ReferenceElement(identifier, typeArgs).assignNoPrototype()
            }
        }

        if (classType is PsiClassReferenceType) {
            return converter.convertCodeReferenceElement(classType.reference, hasExternalQualifier = false, typeArgsConverted = typeArgs)
        }

        return ReferenceElement(Identifier.withNoPrototype(classType.className ?: ""), typeArgs).assignNoPrototype()
    }

    private fun convertTypeArgs(classType: PsiClassType): List<Type> {
        return if (classType.parameterCount == 0) {
            createTypeArgsForRawTypeUsage(classType)
        }
        else {
            typeConverter.convertTypes(classType.parameters)
        }
    }

    private fun createTypeArgsForRawTypeUsage(classType: PsiClassType): List<Type> {
        if (classType is PsiClassReferenceType) {
            val targetClass = classType.reference.resolve() as? PsiClass
            if (targetClass != null) {
                return targetClass.typeParameters.map { StarProjectionType().assignNoPrototype() }
            }
        }
        return listOf()
    }

    override fun visitWildcardType(wildcardType: PsiWildcardType): Type {
        return when {
            wildcardType.isExtends -> OutProjectionType(typeConverter.convertType(wildcardType.extendsBound))
            wildcardType.isSuper -> InProjectionType(typeConverter.convertType(wildcardType.superBound))
            else -> StarProjectionType()
        }
    }

    override fun visitEllipsisType(ellipsisType: PsiEllipsisType): Type {
        return VarArgType(typeConverter.convertType(ellipsisType.componentType, inAnnotationType = inAnnotationType))
    }
}
