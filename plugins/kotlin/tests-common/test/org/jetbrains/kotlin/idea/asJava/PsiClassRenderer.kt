// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.asJava

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiAnnotationMethod
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReferenceList
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiVariable
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.util.MethodSignature
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.analysis.utils.printer.prettyPrint
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.kotlin.NON_EXISTENT_CLASS_NAME
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

open class PsiClassRenderer protected constructor(
    private val renderInner: Boolean,
    private val membersFilter: MembersFilter
) {

    interface MembersFilter {
        fun includeEnumConstant(psiEnumConstant: PsiEnumConstant): Boolean = true
        fun includeField(psiField: PsiField): Boolean = true
        fun includeMethod(psiMethod: PsiMethod): Boolean = true
        fun includeClass(psiClass: PsiClass): Boolean = true

        companion object {
            val DEFAULT = object : MembersFilter {}
        }
    }

    companion object {
        fun renderType(psiType: PsiType): String = with(PsiClassRenderer(renderInner = false, membersFilter = MembersFilter.DEFAULT)) {
            psiType.renderType()
        }
    }

    private fun PrettyPrinter.renderClass(psiClass: PsiClass) {
        val classWord = when {
            psiClass.isAnnotationType -> "@interface"
            psiClass.isInterface -> "interface"
            psiClass.isEnum -> "enum"
            else -> "class"
        }

        append(psiClass.renderModifiers())
        append("$classWord ")
        append("${psiClass.name} /* ${psiClass.qualifiedName}*/")
        append(psiClass.typeParameters.renderTypeParams())
        append(psiClass.extendsList.renderRefList("extends"))
        append(psiClass.implementsList.renderRefList("implements"))
        appendLine(" {")
        withIndent {
            if (psiClass.isEnum) {
                psiClass.fields
                    .filterIsInstance<PsiEnumConstant>()
                    .filter { membersFilter.includeEnumConstant(it) }
                    .joinTo(this, ",\n") { it.renderEnumConstant() }

                append(";\n\n")
            }

            renderMembers(psiClass)
        }

        append("}")
    }

    protected open fun renderClass(psiClass: PsiClass): String = prettyPrint {
        renderClass(psiClass)
    }

    private fun PsiType.renderType() = StringBuffer().also { renderType(it) }.toString()

    private fun PsiType.renderType(sb: StringBuffer) {
        fun renderAnnotations(leadingAnnotations: Boolean) {
            annotations.ifNotEmpty {
                joinTo(
                    buffer = sb,
                    separator = " ",
                    postfix = " ",
                    prefix = if (leadingAnnotations) "" else " ",
                ) { it.renderAnnotation() }
            }
        }

        when (this) {
            is PsiEllipsisType -> {
                componentType.renderType(sb)
                renderAnnotations(leadingAnnotations = false)
                sb.append("...")

                return
            }

            is PsiArrayType -> {
                componentType.renderType(sb)
                renderAnnotations(leadingAnnotations = false)
                sb.append("[]")

                return
            }
        }

        renderAnnotations(leadingAnnotations = true)

        when (this) {
            is PsiClassType -> {
                sb.append(PsiNameHelper.getQualifiedClassName(canonicalText, false))
                if (parameterCount > 0) {
                    sb.append("<")
                    parameters.forEachIndexed { index, type ->
                        type.renderType(sb)
                        if (index < parameterCount - 1) sb.append(", ")
                    }
                    sb.append(">")
                }
            }
            is PsiWildcardType -> {
                if (!isBounded) {
                    sb.append("?")
                } else {
                    if (isSuper) {
                        sb.append(PsiWildcardType.SUPER_PREFIX)
                    } else {
                        sb.append(PsiWildcardType.EXTENDS_PREFIX)
                    }

                    bound?.renderType(sb)
                }
            }
            is PsiPrimitiveType -> {
                sb.append(name)
            }
            else -> {
                sb.append(getCanonicalText(/* annotated = */ true))
            }
        }
    }


    private fun PsiReferenceList?.renderRefList(keyword: String, sortReferences: Boolean = true): String {
        if (this == null) return ""

        val references = referencedTypes
        if (references.isEmpty()) return ""

        val referencesTypes = references.map { it.renderType() }.toTypedArray()

        if (sortReferences) referencesTypes.sort()

        return " " + keyword + " " + referencesTypes.joinToString()
    }

    private fun PsiVariable.renderVar(): String {
        var result = this.renderModifiers(type) + type.renderType() + " " + name
        if (this is PsiParameter && this.isVarArgs) {
            result += " /* vararg */"
        }

        if (hasInitializer()) {
            result += " = ${initializer?.text} /* initializer type: ${initializer?.type?.renderType()} */"
        }

        computeConstantValue()?.let { result += " /* constant value $it */" }

        return result
    }

    private fun Array<PsiTypeParameter>.renderTypeParams() =
        if (isEmpty()) ""
        else "<" + joinToString {
            val extendsListTypes = it.extendsListTypes
            val bounds = if (extendsListTypes.isNotEmpty()) {
                " extends " + extendsListTypes.joinToString(" & ", transform = { it.renderType() })
            } else {
                ""
            }

            it.renderModifiers() + it.name!! + bounds
        } + "> "

    protected open fun PsiAnnotationMemberValue.renderAnnotationMemberValue(): String {
        return text
    }

    private fun PsiMethod.renderMethod() =
        renderModifiers(returnType) +
                (if (isVarArgs) "/* vararg */ " else "") +
                typeParameters.renderTypeParams() +
                (returnType?.renderType() ?: "") + " " +
                name +
                "(" + parameterList.parameters.joinToString { it.renderModifiers(it.type) + it.type.renderType() } + ")" +
                (this as? PsiAnnotationMethod)?.defaultValue?.let { " default " + it.renderAnnotationMemberValue() }.orEmpty() +
                throwsList.referencedTypes.let { thrownTypes ->
                    if (thrownTypes.isEmpty()) ""
                    else " throws " + thrownTypes.joinToString { it.renderType() }
                } +
                ";" +
                "// ${getSignature(PsiSubstitutor.EMPTY).renderSignature()}"

    private fun MethodSignature.renderSignature(): String {
        val typeParams = typeParameters.renderTypeParams()
        val paramTypes = parameterTypes.joinToString(prefix = "(", postfix = ")") { it.renderType() }
        val name = if (isConstructor) ".ctor" else name
        return "$typeParams $name$paramTypes"
    }

    private fun PsiEnumConstant.renderEnumConstant(): String {
        val annotations = this@renderEnumConstant.annotations
            .map { it.renderAnnotation() }
            .filter { it.isNotBlank() }
            .joinToString(separator = " ", postfix = " ")
            .takeIf { it.isNotBlank() }
            ?: ""

        val initializingClass = initializingClass ?: return "$annotations$name"
        return prettyPrint {
            append(annotations)
            appendLine("$name {")
            renderMembers(initializingClass)
            append("}")
        }
    }

    private fun PrettyPrinter.renderMembers(psiClass: PsiClass) {
        var wasRendered = false
        val fields = psiClass.fields.filterNot { it is PsiEnumConstant }.filter { membersFilter.includeField(it) }
        appendSorted(fields, wasRendered) {
            it.renderVar() + ";"
        }

        fields.ifNotEmpty { wasRendered = true }
        val methods = psiClass.methods.filter { membersFilter.includeMethod(it) }
        appendSorted(methods, wasRendered) {
            it.renderMethod()
        }

        methods.ifNotEmpty { wasRendered = true }
        val classes = psiClass.innerClasses.filter { membersFilter.includeClass(it) }
        appendSorted(classes, wasRendered) {
            val name = it.name
            if (renderInner || name == JvmAbi.DEFAULT_IMPLS_CLASS_NAME)
                prettyPrint {
                    renderClass(it)
                }
            else {
                "class $name ..."
            }
        }

        classes.ifNotEmpty { wasRendered = true }
        if (wasRendered) {
            appendLine()
        }
    }

    private fun <T> PrettyPrinter.appendSorted(list: List<T>, addPrefix: Boolean, render: (T) -> String) {
        if (list.isEmpty()) return
        val prefix = if (addPrefix) "\n\n" else ""
        list.map(render).sorted().joinTo(this, separator = "\n\n", prefix = prefix)
    }

    protected fun PsiAnnotation.renderAnnotation(): String {

        if (qualifiedName == "kotlin.Metadata") return ""

        val renderedAttributes = parameterList.attributes.map {
            val attributeValue = it.value?.renderAnnotationMemberValue() ?: "?"

            val isAnnotationQualifiedName =
                (qualifiedName?.startsWith("java.lang.annotation.") == true || qualifiedName?.startsWith("kotlin.annotation.") == true)

            val name = if (it.name == null && isAnnotationQualifiedName) "value" else it.name


            if (name != null) "$name = $attributeValue" else attributeValue
        }

        val renderedAttributesString = renderedAttributes.joinToString()
        if (qualifiedName == null && renderedAttributesString.isEmpty()) {
            return ""
        }
        return "@$qualifiedName(${renderedAttributes.joinToString()})"
    }


    private fun PsiModifierListOwner.renderModifiers(typeIfApplicable: PsiType? = null): String {
        val annotationsBuffer = mutableListOf<String>()
        var nullableIsRendered = false
        var notNullIsRendered = false

        for (annotation in annotations) {
            if (isNullabilityAnnotation(annotation) && skipRenderingNullability(typeIfApplicable)) {
                continue
            }

            if (annotation.qualifiedName == "org.jetbrains.annotations.Nullable") {
                if (nullableIsRendered) continue
                nullableIsRendered = true
            }

            if (annotation.qualifiedName == "org.jetbrains.annotations.NotNull") {
                if (notNullIsRendered) continue
                notNullIsRendered = true
            }

            val renderedAnnotation = annotation.renderAnnotation()
            if (renderedAnnotation.isNotEmpty()) {
                annotationsBuffer.add(
                    renderedAnnotation + (if (this is PsiParameter || this is PsiTypeParameter) " " else "\n")
                )
            }
        }
        annotationsBuffer.sort()

        val resultBuffer = StringBuffer(annotationsBuffer.joinToString(separator = ""))
        for (modifier in PsiModifier.MODIFIERS.filter(::hasModifierProperty)) {
            if (modifier == PsiModifier.DEFAULT) {
                resultBuffer.append(PsiModifier.ABSTRACT).append(" ")
            } else if (modifier != PsiModifier.FINAL || !(this is PsiClass && this.isEnum)) {
                resultBuffer.append(modifier).append(" ")
            }
        }
        return resultBuffer.toString()
    }

    protected open fun isNullabilityAnnotation(annotation: PsiAnnotation?): Boolean = false

    private val NON_EXISTENT_QUALIFIED_CLASS_NAME = NON_EXISTENT_CLASS_NAME.replace("/", ".")

    private fun isPrimitiveOrNonExisting(typeIfApplicable: PsiType?): Boolean {
        if (typeIfApplicable is PsiPrimitiveType) return true
        if (typeIfApplicable?.getCanonicalText(false) == NON_EXISTENT_QUALIFIED_CLASS_NAME) return true

        return false
    }

    private fun PsiModifierListOwner.skipRenderingNullability(typeIfApplicable: PsiType?) =
        isPrimitiveOrNonExisting(typeIfApplicable)// || isPrivateOrParameterInPrivateMethod()

}
