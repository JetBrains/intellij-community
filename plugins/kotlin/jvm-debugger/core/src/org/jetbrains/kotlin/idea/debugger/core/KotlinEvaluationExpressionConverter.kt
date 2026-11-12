// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.engine.evaluation.DebuggerEvaluationExpressionConverter
import com.intellij.debugger.engine.evaluation.TextWithImports
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiArrayAccessExpression
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiTypeElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.idea.KotlinFileType

/**
 * Creates a Kotlin [TextWithImports] from a Java [PsiElement] produced by the debugger tree evaluation chain,
 * replacing Java type casts like `((Type)expr).field` with Kotlin casts (e.g., `(expr as Type).field` for Kotlin).
 * It also replaces the Java collection calls of the Collection and Map renderers that Kotlin cannot evaluate.
 */
internal class KotlinEvaluationExpressionConverter : DebuggerEvaluationExpressionConverter {
    override fun convert(psiExpression: PsiElement): TextWithImports {
        val text = convertToKotlin(psiExpression)
        val imports = psiExpression.getUserData(DebuggerTreeNodeExpression.ADDITIONAL_IMPORTS_KEY)?.let {
            StringUtil.join(it, ",")
        } ?: ""
        return TextWithImportsImpl(CodeFragmentKind.EXPRESSION, text, imports, KotlinFileType.INSTANCE)
    }

    private fun convertToKotlin(element: PsiElement): String = when (element) {
        is PsiTypeCastExpression -> {
            val castType = element.castType ?: return element.text
            val operand = element.operand ?: return element.text
            "${convertToKotlin(operand)} as ${renderCastType(castType)}"
        }

        is PsiParenthesizedExpression -> {
            val inner = element.expression ?: return "()"
            val castOperand = (inner as? PsiTypeCastExpression)?.operand
            if (castOperand != null && isMapEntryElement(castOperand)) {
                // No need to cast a map entry
                return convertToKotlin(castOperand)
            }
            "(${convertToKotlin(inner)})"
        }

        is PsiArrayAccessExpression -> convertCollectionElement(element) ?: convertChildren(element)
        is PsiMethodCallExpression -> convertMapEntryProperty(element) ?: convertChildren(element)
        is PsiReferenceExpression -> {
            // A plugin may rename the field (atomicfu: `_state` to `_state$volatile`); prefill the source name.
            val name = element.referenceName
            val displayName = name?.let { KotlinMangledMemberContributor.getDisplayName(it) }
            val qualifier = element.qualifierExpression
            if (displayName != null && qualifier != null) {
                "${convertToKotlin(qualifier)}.$displayName"
            } else {
                convertChildren(element)
            }
        }

        else -> convertChildren(element)
    }

    private fun convertChildren(element: PsiElement): String {
        val children = element.children
        if (children.isEmpty()) {
            return element.text
        }
        return children.joinToString("") { convertToKotlin(it) }
    }

    /**
     * From the Java side, we get `collection.toArray()[i]` and similar calls.
     * This function converts them to the respective idiomatic Kotlin alternatives, using `elementAt(i)`.
     */
    private fun convertCollectionElement(access: PsiArrayAccessExpression): String? {
        val collection = toArrayCollection(access) ?: return null
        val index = access.indexExpression ?: return null
        val entrySetMap = entrySetMap(collection)
        val kotlinCollection = if (entrySetMap != null) {
            "${convertToKotlin(entrySetMap)}.entries"
        } else {
            convertToKotlin(collection)
        }
        return "$kotlinCollection.elementAt(${convertToKotlin(index)})"
    }

    /**
     * Given the getter/setter usage in [call] of a Map entry, returns an expression using the Kotlin properties instead.
     * Also converts runtime casts to the class of entries to the Kotlin type `Map.Entry<*, *>`.
     */
    private fun convertMapEntryProperty(call: PsiMethodCallExpression): String? {
        val getterName = call.methodExpression.referenceName ?: return null
        val propertyName = when (getterName) {
            "getKey" -> "key"
            "getValue" -> "value"
            else -> return null
        }
        val entry = call.methodExpression.qualifierExpression ?: return null
        if (!call.isCallWithoutArguments(getterName, CommonClassNames.JAVA_UTIL_MAP_ENTRY)) return null
        val entryCast = PsiUtil.skipParenthesizedExprDown(entry) as? PsiTypeCastExpression
        val castOperand = entryCast?.operand
        val castClass = entryCast?.castType?.let(::resolveCastClass)
        // We need to change the cast to use the Kotlin Map.Entry class.
        // Internal Map entry classes might be inaccessible.
        if (castOperand != null && !isMapEntryElement(castOperand) && castClass.isMapEntryInheritor()) {
            return "(${convertToKotlin(castOperand)} as Map.Entry<*, *>).$propertyName"
        }
        return "${convertToKotlin(entry)}.$propertyName"
    }

    /**
     * Returns if [this] corresponds to a subtype of a Map entry.
     */
    private fun PsiClass?.isMapEntryInheritor(): Boolean =
        InheritanceUtil.isInheritor(this, CommonClassNames.JAVA_UTIL_MAP_ENTRY)

    /**
     * Checks if the [expression] matches `map.entrySet().toArray()[i]`.
     */
    private fun isMapEntryElement(expression: PsiExpression): Boolean =
        entrySetMap(toArrayCollection(expression)) != null

    /**
     * Returns `collection` for `collection.toArray()[i]`.
     */
    private fun toArrayCollection(expression: PsiExpression): PsiExpression? {
        val access = PsiUtil.skipParenthesizedExprDown(expression) as? PsiArrayAccessExpression ?: return null
        val toArray = access.arrayExpression as? PsiMethodCallExpression ?: return null
        if (!toArray.isCallWithoutArguments("toArray", CommonClassNames.JAVA_UTIL_COLLECTION)) return null
        return toArray.methodExpression.qualifierExpression
    }

    /**
     * Returns `map` for `map.entrySet()`.
     */
    private fun entrySetMap(expression: PsiExpression?): PsiExpression? {
        val entrySet = expression as? PsiMethodCallExpression ?: return null
        if (!entrySet.isCallWithoutArguments("entrySet", CommonClassNames.JAVA_UTIL_MAP)) return null
        return entrySet.methodExpression.qualifierExpression
    }

    /**
     * Checks that the call is called [name], has no arguments, and resolves to a member of [ownerClassName] or a subclass.
     * If the call is unresolved we also return true because the Java PSI does not resolve Kotlin variables.
     */
    private fun PsiMethodCallExpression.isCallWithoutArguments(name: String, ownerClassName: String): Boolean {
        if (methodExpression.referenceName != name || !argumentList.isEmpty) return false
        val method = resolveMethod() ?: return true
        return InheritanceUtil.isInheritor(method.containingClass, ownerClassName)
    }

    /**
     * Java runtime-type casts are raw: generic arguments are dropped. A raw Kotlin cast such as `x as Box`
     * does not compile for a generic class, so re-add star projections, e.g. `x as Box<*>` or
     * `x as Map<*, *>` (IDEA-335226).
     * An inner class also needs the star projections of its generic outer class, e.g. `x as java.util.LinkedHashMap<*, *>.LinkedKeySet`.
     * That outer class gets its qualified name, because a short name such as `LinkedHashMap` can resolve to a Kotlin type alias.
     *
     * We only need the arity, straight from the resolved Java [PsiClassType]. No Analysis API here: its
     * `defaultTypeWithStarProjections` wants a `KaClassifierSymbol` and an `analyze {}` session. Overkill for
     * appending `*`s in a PSI to text step.
     */
    private fun renderCastType(castType: PsiTypeElement): String {
        val text = castType.text
        if ('<' in text) return text
        val psiClass = resolveCastClass(castType) ?: return text
        // The class first, then each outer class of an inner class.
        val classes = generateSequence(psiClass) { if (PsiUtil.isInnerClass(it)) it.containingClass else null }.toList()
        return when (val outermostGeneric = classes.indexOfLast { it.typeParameters.isNotEmpty() }) {
            -1 -> text
            0 -> text + starProjections(psiClass)
            else -> classes.take(outermostGeneric + 1).asReversed().mapIndexed { i, segmentClass ->
                val name = if (i == 0) segmentClass.qualifiedName else segmentClass.name
                (name ?: return text) + starProjections(segmentClass)
            }.joinToString(".")
        }
    }

    private fun starProjections(psiClass: PsiClass): String {
        val arity = psiClass.typeParameters.size
        return if (arity == 0) "" else List(arity) { "*" }.joinToString(separator = ", ", prefix = "<", postfix = ">")
    }

    private fun resolveCastClass(castType: PsiTypeElement): PsiClass? =
        (castType.type as? PsiClassType)?.resolve() ?: findImportedClass(castType)

    /**
     * Finds the class of a cast type that does not resolve in the Java PSI.
     * The platform writes a runtime cast type by its short name.
     * It adds the qualified name to [DebuggerTreeNodeExpression.ADDITIONAL_IMPORTS_KEY].
     */
    private fun findImportedClass(castType: PsiTypeElement): PsiClass? {
        val text = castType.text
        val root = PsiTreeUtil.getTopmostParentOfType(castType, PsiExpression::class.java) ?: return null
        val imports = root.getUserData(DebuggerTreeNodeExpression.ADDITIONAL_IMPORTS_KEY) ?: return null
        val qualifiedName = imports.firstOrNull { it == text || it.endsWith(".$text") } ?: return null
        val project = castType.project
        return JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.allScope(project))
    }
}
