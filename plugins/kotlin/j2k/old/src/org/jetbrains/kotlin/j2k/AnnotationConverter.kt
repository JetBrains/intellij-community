// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k

import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.idea.j2k.content
import org.jetbrains.kotlin.j2k.ast.*
import org.jetbrains.kotlin.j2k.ast.Annotation
import org.jetbrains.kotlin.load.java.components.JavaAnnotationTargetMapper
import org.jetbrains.kotlin.name.FqName
import java.lang.annotation.ElementType
import java.lang.annotation.Target

class AnnotationConverter(private val converter: Converter) {
    private val annotationsToRemove: Set<String> = (NullableNotNullManager.getInstance(converter.project).notNulls
                                                    + NullableNotNullManager.getInstance(converter.project).nullables
                                                    + listOf(CommonClassNames.JAVA_LANG_OVERRIDE, ElementType::class.java.name, SuppressWarnings::class.java.name)).toSet()

    fun isImportNotRequired(fqName: FqName): Boolean {
        val nameAsString = fqName.asString()
        return nameAsString in annotationsToRemove || nameAsString == Target::class.java.name
    }

    fun convertAnnotations(owner: PsiModifierListOwner, target: AnnotationUseTarget? = null): Annotations
            = convertAnnotationsOnly(owner, target) + convertModifiersToAnnotations(owner, target)

    private fun convertAnnotationsOnly(owner: PsiModifierListOwner, target: AnnotationUseTarget?): Annotations {
        val modifierList = owner.modifierList
        val annotations = modifierList?.annotations?.filter { it.qualifiedName !in annotationsToRemove }

        var convertedAnnotations: List<Annotation> = if (!annotations.isNullOrEmpty()) {
            val newLines = if (!modifierList.isInSingleLine()) {
                true
            }
            else {
                var child: PsiElement? = modifierList
                while (true) {
                    child = child!!.nextSibling
                    if (child == null || child.textLength != 0) break
                }
                if (child is PsiWhiteSpace) !child.isInSingleLine() else false
            }

            annotations.mapNotNull { convertAnnotation(it, newLineAfter = newLines, target = target) }
        }
        else {
            listOf()
        }

        if (owner is PsiDocCommentOwner) {
            val deprecatedAnnotation = convertDeprecatedJavadocTag(owner, target)
            if (deprecatedAnnotation != null) {
                convertedAnnotations = convertedAnnotations.filter { it.name.name != deprecatedAnnotation.name.name }
                convertedAnnotations += deprecatedAnnotation
            }
        }

        return Annotations(convertedAnnotations).assignNoPrototype()
    }

    private fun convertDeprecatedJavadocTag(element: PsiDocCommentOwner, target: AnnotationUseTarget?): Annotation? {
        val deprecatedTag = element.docComment?.findTagByName("deprecated") ?: return null
        val deferredExpression = converter.deferredElement<Expression> {
            val text = buildString {
                val split = deprecatedTag.content().split("\n")
                val length = split.size
                split.forEachIndexed { index, s ->
                    if (index > 0) append("+\n")
                    val content = if (index == length - 1) s else s + "\n"
                    append("\"" + StringUtil.escapeStringCharacters(content) + "\"")
                }
            }
            LiteralExpression(text).assignNoPrototype()
        }
        val identifier = Identifier("Deprecated").assignPrototype(deprecatedTag.nameElement)
        return Annotation(identifier,
                          listOf(null to deferredExpression),
                          true,
                          effectiveAnnotationUseTarget(identifier.name, target))
                .assignPrototype(deprecatedTag)
    }

    private fun convertModifiersToAnnotations(owner: PsiModifierListOwner, target: AnnotationUseTarget?): Annotations {
        val list = MODIFIER_TO_ANNOTATION
                .filter { owner.hasModifierProperty(it.first) }
                .map {
                    Annotation(Identifier.withNoPrototype(it.second),
                               listOf(),
                               newLineAfter = false,
                               target = target
                    ).assignNoPrototype()
                }
        return Annotations(list).assignNoPrototype()
    }

    private val MODIFIER_TO_ANNOTATION = listOf(
            PsiModifier.SYNCHRONIZED to "Synchronized",
            PsiModifier.VOLATILE to "Volatile",
            PsiModifier.STRICTFP to "Strictfp",
            PsiModifier.TRANSIENT to "Transient"
    )

    private fun mapTargetByName(expr: PsiReferenceExpression): Set<KotlinTarget> {
        return expr.referenceName?.let { JavaAnnotationTargetMapper.mapJavaTargetArgumentByName(it) } ?: emptySet()
    }

    private fun effectiveAnnotationUseTarget(name: String, target: AnnotationUseTarget?): AnnotationUseTarget? =
            when {
                name == "Deprecated" &&
                (target == AnnotationUseTarget.Param || target == AnnotationUseTarget.Field) -> null
                else -> target
            }

    fun convertAnnotation(annotation: PsiAnnotation, newLineAfter: Boolean, target: AnnotationUseTarget? = null): Annotation? {
        val (name, arguments) = convertAnnotationValue(annotation) ?: return null
        return Annotation(name, arguments, newLineAfter, effectiveAnnotationUseTarget(name.name, target)).assignPrototype(annotation, CommentsAndSpacesInheritance.NO_SPACES)
    }

    private fun convertAnnotationValue(annotation: PsiAnnotation): Pair<Identifier, List<Pair<Identifier?, DeferredElement<Expression>>>>? {
        val qualifiedName = annotation.qualifiedName
        if (qualifiedName == CommonClassNames.JAVA_LANG_DEPRECATED && annotation.parameterList.attributes.isEmpty()) {
            val deferredExpression = converter.deferredElement<Expression> { LiteralExpression("\"\"").assignNoPrototype() }
            return Identifier.withNoPrototype("Deprecated") to listOf(null to deferredExpression) //TODO: insert comment
        }
        if (qualifiedName == CommonClassNames.JAVA_LANG_ANNOTATION_TARGET) {
            val attributes = annotation.parameterList.attributes
            val arguments: Set<KotlinTarget> = if (attributes.isEmpty()) {
                setOf()
            }
            else {
                when (val value = attributes[0].value) {
                    is PsiArrayInitializerMemberValue -> value.initializers.filterIsInstance<PsiReferenceExpression>()
                            .flatMap { mapTargetByName(it) }
                            .toSet()
                    is PsiReferenceExpression -> mapTargetByName(value)
                    else -> setOf()
                }
            }
            val deferredExpressionList = arguments.map {
                val name = it.name
                null to converter.deferredElement<Expression> {
                    QualifiedExpression(Identifier.withNoPrototype("AnnotationTarget", isNullable = false),
                                        Identifier.withNoPrototype(name, isNullable = false),
                                        null)
                }
            }
            return Identifier.withNoPrototype("Target") to deferredExpressionList
        }

        val nameRef = annotation.nameReferenceElement
        val name = Identifier((nameRef ?: return null).text!!).assignPrototype(nameRef)
        val annotationClass = nameRef.resolve() as? PsiClass
        val arguments = annotation.parameterList.attributes.flatMap {
            val parameterName = it.name ?: "value"
            val method = annotationClass?.findMethodsByName(parameterName, false)?.firstOrNull()
            val expectedType = method?.returnType

            val attrName = it.name?.let { Identifier.withNoPrototype(it) }
            val value = it.value

            val isVarArg = parameterName == "value" /* converted to vararg in Kotlin */
            val attrValues = convertAttributeValue(value, expectedType, isVarArg, it.name == null)

            attrValues.map { attrName to converter.deferredElement(it) }
        }
        return name to arguments
    }

    fun convertAnnotationMethodDefault(method: PsiAnnotationMethod): DeferredElement<Expression>? {
        val value = method.defaultValue ?: return null
        val returnType = method.returnType
        if (returnType is PsiArrayType && value !is PsiArrayInitializerMemberValue) {
            return converter.deferredElement { codeConverter ->
                converter.typeConverter.convertType(returnType) as ArrayType
                val convertAttributeValue = convertAttributeValue(value, returnType.componentType, isVararg = false, isUnnamed = false)
                createArrayLiteralExpression(codeConverter, convertAttributeValue.toList())
            }
        }
        return converter.deferredElement(convertAttributeValue(value, returnType, isVararg = false, isUnnamed = false).single())
    }

    private fun convertAttributeValue(
        value: PsiAnnotationMemberValue?,
        expectedType: PsiType?,
        isVararg: Boolean,
        isUnnamed: Boolean
    ): List<(CodeConverter) -> Expression> = when (value) {
        is PsiExpression -> listOf { codeConverter -> convertExpressionValue(codeConverter, value, expectedType, isVararg) }

        is PsiArrayInitializerMemberValue -> {
            val componentType = (expectedType as? PsiArrayType)?.componentType
            val componentGenerators = value.initializers.map { convertAttributeValue(it, componentType, false, true).single() }
            if (isVararg && isUnnamed) {
                componentGenerators
            } else {
                listOf { codeConverter ->
                    convertArrayInitializerValue(codeConverter, value.text, componentGenerators, expectedType)
                        .assignPrototype(value)
                }
            }
        }

        is PsiAnnotation -> {
            val annotationConstructor = listOf<(CodeConverter) -> Expression> {
                val (name, arguments) = convertAnnotationValue(value)!!
                AnnotationConstructorCall(name, arguments).assignPrototype(value)
            }
            if (expectedType is PsiArrayType) {
                listOf { codeConverter ->
                    convertArrayInitializerValue(codeConverter, value.text, annotationConstructor, expectedType)
                        .assignPrototype(value)
                }

            } else {
                annotationConstructor
            }
        }
        else -> listOf { DummyStringExpression(value?.text ?: "").assignPrototype(value) }
    }

    private fun convertExpressionValue(codeConverter: CodeConverter, value: PsiExpression, expectedType: PsiType?, isVararg: Boolean): Expression {
        val expression = if (value is PsiClassObjectAccessExpression) {
            val type = converter.convertTypeElement(value.operand, Nullability.NotNull)
            ClassLiteralExpression(type)
        }
        else {
            codeConverter.convertExpression(value, expectedType)
        }.assignPrototype(value)

        if (expectedType is PsiArrayType && !isVararg) {
            return convertArrayInitializerValue(codeConverter,
                                                value.text,
                                                listOf { expression },
                                                expectedType
            ).assignPrototype(value)
        }
        return expression
    }

    private fun convertArrayInitializerValue(
      codeConverter: CodeConverter,
      valueText: String,
      componentGenerators: List<(CodeConverter) -> Expression>,
      expectedType: PsiType?
    ): Expression {
        val expectedTypeConverted = converter.typeConverter.convertType(expectedType)
        return if (expectedTypeConverted is ArrayType) {
            createArrayLiteralExpression(codeConverter, componentGenerators)
        }
        else {
            DummyStringExpression(valueText)
        }
    }

    private fun createArrayLiteralExpression(
        codeConverter: CodeConverter,
        componentGenerators: List<(CodeConverter) -> Expression>
    ): Expression {
        val initializers = componentGenerators.map { it(codeConverter) }
        return ArrayLiteralExpression(initializers)
    }
}
