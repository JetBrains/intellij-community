// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.processings

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.FIELD
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics.findAnnotation
import org.jetbrains.kotlin.idea.core.setVisibility
import org.jetbrains.kotlin.idea.intentions.addUseSiteTarget
import org.jetbrains.kotlin.idea.j2k.post.processing.ElementsBasedPostProcessing
import org.jetbrains.kotlin.idea.j2k.post.processing.runUndoTransparentActionInEdt
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens.DATA_KEYWORD
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.escaped
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.asAssignment
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class MergePropertyWithConstructorParameterProcessing : ElementsBasedPostProcessing() {
    override fun runProcessing(elements: List<PsiElement>, converterContext: NewJ2kConverterContext) {
        for (klass in runReadAction { elements.descendantsOfType<KtClass>() }) {
            klass.convert()
        }
    }

    private fun KtClass.convert() {
        val initializations = runReadAction { collectPropertyInitializations(this) }
        runUndoTransparentActionInEdt(inWriteAction = true) {
            initializations.forEach(::convertInitialization)
            removeEmptyInitBlocks()
            removeRedundantEnumSemicolon()
            removeIllegalDataModifierIfNeeded()
            removeEmptyClassBody()
        }
    }

    private fun collectPropertyInitializations(klass: KtClass): List<Initialization<*>> {
        val usedParameters = mutableSetOf<KtParameter>()
        val usedProperties = mutableSetOf<KtProperty>()
        val initializations = mutableListOf<Initialization<*>>()

        fun KtExpression.asProperty() = unpackedReferenceToProperty()?.takeIf {
            it !in usedProperties && it.containingClass() == klass && it.initializer == null
        }

        fun KtReferenceExpression.asParameter() = resolve()?.safeAs<KtParameter>()?.takeIf {
            it !in usedParameters && it.containingClass() == klass && !it.hasValOrVar()
        }

        fun KtProperty.isSameTypeAs(parameter: KtParameter): Boolean {
            val propertyType = type() ?: return false
            val parameterType = parameter.type() ?: return false
            return KotlinTypeChecker.DEFAULT.equalTypes(propertyType, parameterType)
        }

        fun collectInitialization(expression: KtExpression): Boolean {
            val assignment = expression.asAssignment() ?: return false
            val property = assignment.left?.asProperty() ?: return false
            usedProperties += property
            when (val rightSide = assignment.right) {
                is KtReferenceExpression -> {
                    val parameter = rightSide.asParameter() ?: return false
                    if (!property.isSameTypeAs(parameter)) return false
                    usedParameters += parameter
                    initializations += ConstructorParameterInitialization(property, parameter, assignment)
                }

                is KtConstantExpression, is KtStringTemplateExpression -> {
                    initializations += LiteralInitialization(property, rightSide, assignment)
                }

                else -> {}
            }
            return true
        }

        val initializer = klass.getAnonymousInitializers().singleOrNull() ?: return emptyList()
        val statements = initializer.body?.safeAs<KtBlockExpression>()?.statements ?: return emptyList()
        for (statement in statements) {
            if (!collectInitialization(statement)) break
        }
        return initializations
    }

    private fun convertInitialization(initialization: Initialization<*>) {
        val commentSaver = CommentSaver(initialization.assignment, saveLineBreaks = true)
        val restoreCommentsTarget: KtExpression
        when (initialization) {
            is ConstructorParameterInitialization -> {
                initialization.mergePropertyAndConstructorParameter()
                restoreCommentsTarget = initialization.initializer
            }

            is LiteralInitialization -> {
                val (property, initializer, _) = initialization
                property.initializer = initializer
                restoreCommentsTarget = property
            }
        }

        initialization.assignment.getExplicitLabelComment()?.delete()
        initialization.assignment.delete()
        commentSaver.restore(restoreCommentsTarget, forceAdjustIndent = false)
    }

    private fun ConstructorParameterInitialization.mergePropertyAndConstructorParameter() {
        val (property, parameter, _) = this

        parameter.addBefore(property.valOrVarKeyword, parameter.nameIdentifier!!)
        parameter.addAfter(KtPsiFactory(property.project).createWhiteSpace(), parameter.valOrVarKeyword!!)
        parameter.rename(property.name!!)
        parameter.setVisibility(property.visibilityModifierTypeOrDefault())
        val commentSaver = CommentSaver(property)

        parameter.annotationEntries.forEach {
            if (it.useSiteTarget == null) it.addUseSiteTarget(CONSTRUCTOR_PARAMETER, property.project)
        }
        property.annotationEntries.forEach {
            parameter.addAnnotationEntry(it).also { entry ->
                if (entry.useSiteTarget == null) entry.addUseSiteTarget(FIELD, property.project)
            }
        }
        property.typeReference?.annotationEntries?.forEach { entry ->
            if (parameter.typeReference?.annotationEntries?.all { it.shortName != entry.shortName } == true) {
                parameter.typeReference?.addAnnotationEntry(entry)
            }
        }

        property.delete()
        commentSaver.restore(parameter, forceAdjustIndent = false)
    }

    private fun KtCallableDeclaration.rename(newName: String) {
        val psiFactory = KtPsiFactory(project)
        val escapedName = newName.escaped()
        ReferencesSearch.search(this, LocalSearchScope(containingKtFile)).forEach {
            it.element.replace(psiFactory.createExpression(escapedName))
        }
        setName(escapedName)
    }

    private fun KtClass.removeEmptyInitBlocks() {
        for (initBlock in getAnonymousInitializers()) {
            if ((initBlock.body as KtBlockExpression).statements.isEmpty()) {
                val commentSaver = CommentSaver(initBlock)
                initBlock.delete()
                primaryConstructor?.let { commentSaver.restore(it) }
            }
        }
    }

    private fun KtClass.removeRedundantEnumSemicolon() {
        if (!isEnum()) return
        val enumEntries = body?.childrenOfType<KtEnumEntry>().orEmpty()
        val otherMembers = body?.childrenOfType<KtDeclaration>()?.filterNot { it is KtEnumEntry }.orEmpty()
        if (otherMembers.isNotEmpty()) return
        if (enumEntries.isNotEmpty()) {
            enumEntries.lastOrNull()?.removeSemicolon()
        } else {
            body?.removeSemicolon()
        }
    }

    private fun KtElement.removeSemicolon() {
        getChildrenOfType<LeafPsiElement>().find { it.text == ";" }?.delete()
    }

    private fun KtClass.removeIllegalDataModifierIfNeeded() {
        if (!isData()) return
        if (primaryConstructorParameters.isEmpty() ||
            primaryConstructorParameters.any { it.isVarArg || !it.hasValOrVar() }
        ) {
            removeModifier(DATA_KEYWORD)
            findAnnotation(declaration = this, FqName("kotlin.jvm.JvmRecord"))?.delete()
        }
    }

    private fun KtClass.removeEmptyClassBody() {
        val body = body ?: return
        if (body.declarations.isEmpty()) {
            val commentSaver = CommentSaver(body)
            body.delete()
            commentSaver.restore(resultElement = this)
        }
    }
}

private sealed class Initialization<I : KtElement> {
    abstract val property: KtProperty
    abstract val initializer: I
    abstract val assignment: KtBinaryExpression
}

private data class ConstructorParameterInitialization(
    override val property: KtProperty,
    override val initializer: KtParameter,
    override val assignment: KtBinaryExpression
) : Initialization<KtParameter>()

private data class LiteralInitialization(
    override val property: KtProperty,
    override val initializer: KtExpression,
    override val assignment: KtBinaryExpression
) : Initialization<KtExpression>()
