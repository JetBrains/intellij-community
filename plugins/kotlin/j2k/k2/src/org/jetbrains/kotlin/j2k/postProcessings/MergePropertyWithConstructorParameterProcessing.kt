// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.postProcessings

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.siblings
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationTarget
import org.jetbrains.kotlin.analysis.api.symbols.applicableAnnotationTargets
import org.jetbrains.kotlin.analysis.api.types.expandedSymbol
import org.jetbrains.kotlin.analysis.api.types.type
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.components.returnType
import org.jetbrains.kotlin.analysis.api.types.semanticallyEquals
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.FIELD
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics.findAnnotation
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.ElementsBasedPostProcessing
import org.jetbrains.kotlin.j2k.PostProcessingApplier
import org.jetbrains.kotlin.j2k.resolve
import org.jetbrains.kotlin.j2k.unpackedReferenceToProperty
import org.jetbrains.kotlin.lexer.KtTokens.DATA_KEYWORD
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.j2k.descendantsOfType
import org.jetbrains.kotlin.j2k.escaped
import org.jetbrains.kotlin.j2k.getExplicitLabelComment
import org.jetbrains.kotlin.j2k.runUndoTransparentActionInEdt
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange
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
    override fun computeApplier(elements: List<PsiElement>): PostProcessingApplier {
        val context = prepareContext(elements)
        return Applier(context)
    }

    private fun prepareContext(elements: List<PsiElement>): Map<KtClass, List<Initialization<*>>> {
        val context = mutableMapOf<KtClass, List<Initialization<*>>>()

        for (klass in elements.descendantsOfType<KtClass>()) {
            analyze(klass) {
                val initializations = collectPropertyInitializations(klass)
                context[klass] = initializations
            }
        }

        return context
    }

    /**
     * On a constructor `val`/`var` an annotation without a use-site target goes to the first applicable of
     * parameter, property, field — so `@field:` only changes anything when the annotation could land elsewhere.
     */
    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    private fun KtAnnotationEntry.needsFieldUseSiteTarget(): Boolean {
        val targets = typeReference?.type?.expandedSymbol?.applicableAnnotationTargets ?: return true
        return KaAnnotationTarget.VALUE_PARAMETER in targets || KaAnnotationTarget.PROPERTY in targets
    }

    context(_: KaSession)
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
                    val references = ReferencesSearch.search(parameter, LocalSearchScope(parameter.containingKtFile)).asIterable().toList()
                    val needFieldTarget = property.annotationEntries.filterTo(mutableSetOf()) { it.needsFieldUseSiteTarget() }
                    initializations += ConstructorParameterInitialization(
                        property, parameter, assignment, references, needFieldTarget
                    )
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
        commentSaver.restoreKeepingIndent(PsiChildRange.singleElement(restoreCommentsTarget))
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

        // CommentSaver(property) alone only captures comments that are descendants of `property`. Other
        // processings (e.g. K2ConvertGettersAndSettersToPropertyProcessing) can leave a property's
        // getter/setter comments as preceding SIBLINGS in the class body instead, so also pull those in.
        val firstLeadingComment = property.siblings(forward = false, withSelf = false)
            .takeWhile { it is PsiWhiteSpace || it is PsiComment }
            .lastOrNull { it is PsiComment } ?: property
        val commentSaver = CommentSaver(PsiChildRange(firstLeadingComment, property))

        parameter.annotationEntries.forEach {
            if (it.useSiteTarget == null) it.addUseSiteTarget(CONSTRUCTOR_PARAMETER)
        }
        property.annotationEntries.forEach { propertyAnnotation ->
            val entry = parameter.addAnnotationEntry(propertyAnnotation)
            if (entry.useSiteTarget == null && propertyAnnotation in annotationsNeedingFieldTarget) {
                entry.addUseSiteTarget(FIELD)
            }
        }
        property.typeReference?.annotationEntries?.forEach { entry ->
            if (parameter.typeReference?.annotationEntries?.all { it.shortName != entry.shortName } == true) {
                parameter.typeReference?.addAnnotationEntry(entry)
            }
        }

        val hasLeadingComments = firstLeadingComment != property
        if (hasLeadingComments) {
            property.parent.deleteChildRange(firstLeadingComment, property)
        } else {
            property.delete()
        }
        // A lone trailing comment on the property (no accessor comments merged in alongside it) keeps
        // its long-standing placement as a leading comment on the parameter. Only preserve it as
        // trailing when there are also leading comments here to keep distinct from it — otherwise the
        // property's own comment would be indistinguishable from an accessor's once both are leading.
        commentSaver.restoreKeepingIndent(PsiChildRange.singleElement(parameter), preserveTrailingComments = hasLeadingComments)
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
                commentSaver.restoreKeepingIndent(PsiChildRange.singleElement(target))
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
            commentSaver.restoreKeepingIndent(PsiChildRange.singleElement(this))
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
    val parameterReferences: List<PsiReference>,
    val annotationsNeedingFieldTarget: Set<KtAnnotationEntry>,
) : Initialization<KtParameter>()

private data class LiteralInitialization(
    override val property: KtProperty,
    override val initializer: KtExpression,
    override val assignment: KtBinaryExpression
) : Initialization<KtExpression>()
