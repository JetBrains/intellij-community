// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.postProcessings

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.FIELD
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics.findAnnotation
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.j2k.ElementsBasedPostProcessing
import org.jetbrains.kotlin.j2k.PostProcessingApplier
import org.jetbrains.kotlin.j2k.resolve
import org.jetbrains.kotlin.j2k.unpackedReferenceToProperty
import org.jetbrains.kotlin.lexer.KtTokens.DATA_KEYWORD
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.asAssignment
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * 1. Merges the triple "primary constructor parameter / property / initialization of property with parameter"
 * into a single property declaration to produce more clean and idiomatic Kotlin code.
 *
 * 2. Also works for simple property initializers in `init` blocks without parameters (similar to JoinDeclarationAndAssignmentInspection).
 *
 * 3. Currently, `RecordClassConversion` depends on this processing (in order to produce a valid data class,
 * regular properties must be merged into the primary constructor).
 *
 * TODO convert everything to element pointers
 */
class MergePropertyWithConstructorParameterProcessing : ElementsBasedPostProcessing() {
    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun runProcessing(elements: List<PsiElement>, converterContext: NewJ2kConverterContext) {
        val ktElement = elements.firstIsInstanceOrNull<KtElement>() ?: return
        val context = runReadAction {
            allowAnalysisOnEdt {
                analyze(ktElement) {
                    prepareContext(elements)
                }
            }
        }

        runUndoTransparentActionInEdt(inWriteAction = true) {
            Applier(context).apply()
        }
    }

    context(KaSession)
    override fun computeApplier(elements: List<PsiElement>, converterContext: NewJ2kConverterContext): PostProcessingApplier {
        val context = prepareContext(elements)
        return Applier(context)
    }

    context(KaSession)
    private fun prepareContext(elements: List<PsiElement>): Map<KtClass, List<Initialization<*>>> {
        val context = mutableMapOf<KtClass, List<Initialization<*>>>()

        for (klass in elements.descendantsOfType<KtClass>()) {
            val initializations = collectPropertyInitializations(klass)
            context[klass] = initializations
        }

        return context
    }

    context(KaSession)
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
            val propertyType = this.symbol.returnType
            val parameterType = parameter.returnType // this is taking varargs into account (KT-64340)
            return propertyType.semanticallyEquals(parameterType)
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
                    val references = ReferencesSearch.search(parameter, LocalSearchScope(parameter.containingKtFile)).toList()
                    initializations += ConstructorParameterInitialization(property, parameter, assignment, references)
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
}

private class Applier(private val context: Map<KtClass, List<Initialization<*>>>) : PostProcessingApplier {
    override fun apply() {
        for ((klass, initializations) in context) {
            for (initialization in initializations) {
                convertInitialization(initialization)
            }

            with(klass) {
                removeEmptyInitBlocks()
                removeRedundantEnumSemicolon()
                removeIllegalDataModifierIfNeeded()
                removeEmptyClassBody()
            }
        }
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
        parameter.rename(property.name!!, this.parameterReferences)

        val visibilityModifier = property.visibilityModifierType()
        if (visibilityModifier != null) {
            parameter.addModifier(visibilityModifier)
        }

        val commentSaver = CommentSaver(property)

        parameter.annotationEntries.forEach {
            if (it.useSiteTarget == null) it.addUseSiteTarget(CONSTRUCTOR_PARAMETER)
        }
        property.annotationEntries.forEach {
            parameter.addAnnotationEntry(it).also { entry ->
                if (entry.useSiteTarget == null) entry.addUseSiteTarget(FIELD)
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

    private fun KtAnnotationEntry.addUseSiteTarget(useSiteTarget: AnnotationUseSiteTarget) {
        project.executeWriteCommand("") {
            replace(KtPsiFactory(this.project).createAnnotationEntry("@${useSiteTarget.renderName}:${text.drop(1)}"))
        }
    }

    private fun KtParameter.rename(newName: String, parameterReferences: List<PsiReference>) {
        val psiFactory = KtPsiFactory(project)
        val escapedName = newName.escaped()
        for (reference in parameterReferences) {
            reference.element.replace(psiFactory.createExpression(escapedName))
        }
        setName(escapedName)
    }

    private fun KtClass.removeEmptyInitBlocks() {
        for (initBlock in getAnonymousInitializers()) {
            if ((initBlock.body as KtBlockExpression).statements.isEmpty()) {
                val commentSaver = CommentSaver(initBlock)
                initBlock.delete()
                val target = primaryConstructor ?: this
                commentSaver.restore(target)
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
    override val assignment: KtBinaryExpression,
    val parameterReferences: List<PsiReference>
) : Initialization<KtParameter>()

private data class LiteralInitialization(
    override val property: KtProperty,
    override val initializer: KtExpression,
    override val assignment: KtBinaryExpression
) : Initialization<KtExpression>()
