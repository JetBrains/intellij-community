// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import org.jetbrains.kotlin.j2k.ast.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.jvm.JvmPrimitiveType

private val PRIMITIVE_TYPES_NAMES = JvmPrimitiveType.values().map { it.javaKeywordName }

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
