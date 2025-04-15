// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesDialog
import com.intellij.refactoring.move.moveInner.MoveInnerClassUsagesHandler
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usageView.UsageViewUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.deleteSingle
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor.Declaration
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor.Declaration.DeclarationTargetType
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages.K2MoveRenameUsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages.K2MoveRenameUsageInfo.Companion.markInternalUsages
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages.OuterInstanceReferenceUsageInfo
import org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringListener
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly

open class K2MoveDeclarationsRefactoringProcessor(
    private val operationDescriptor: K2MoveOperationDescriptor.Declarations
) : BaseRefactoringProcessor(operationDescriptor.project) {
    companion object {
        const val REFACTORING_ID: String = "move.kotlin.declarations"
    }

    override fun getRefactoringId(): String = REFACTORING_ID

    override fun getCommandName(): String = KotlinBundle.message("command.move.declarations")

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor = operationDescriptor.usageViewDescriptor()

    private fun getUsages(moveDescriptor: K2MoveDescriptor): List<UsageInfo> {
        val usages = moveDescriptor.source.elements
            .filterIsInstance<KtNamedDeclaration>()
            .flatMap { elem ->
                // We filter out constructors because calling bindTo on these references will break for light classes.
                if (elem is KtPrimaryConstructor || elem is KtSecondaryConstructor) return@flatMap emptyList()
                if (operationDescriptor.searchReferences) {
                    elem.findUsages(
                        searchInCommentsAndStrings = operationDescriptor.searchInComments,
                        searchForText = operationDescriptor.searchForText,
                        moveTarget = moveDescriptor.target
                    )
                } else {
                    // If we do not search for references, we still need to mark internal usages so that
                    // imports are copied over correctly.
                    // In the branch above, markInternalUsages is called as part of findUsages.
                    markInternalUsages(elem, elem)
                    emptyList()
                }
            }
        if (!operationDescriptor.searchReferences) return usages
        return usages + findInternalOuterInstanceUsages(moveDescriptor.source)
    }

    protected override fun findUsages(): Array<UsageInfo> {
        val allUsages = operationDescriptor.moveDescriptors.flatMap { moveDescriptor ->
            val usages = operationDescriptor.moveDescriptors.flatMapTo(mutableSetOf(), ::getUsages)
            if (operationDescriptor.searchReferences) {
                collectConflicts(usages)
            }
            usages
        }.toTypedArray()

        return UsageViewUtil.removeDuplicatedUsages(allUsages)
    }

    /**
     * We consider a file as effectively empty (which implies it can be safely deleted)
     * if it contains only package, import and file annotation statements and comments before the
     * package directive (which are usually copyright notices).
     */
    private fun KtFile.isEffectivelyEmpty(): Boolean {
        if (declarations.isNotEmpty()) return false
        val packageDeclaration = packageDirective
        return children.all {
            it is PsiWhiteSpace ||
                    it is KtImportList ||
                    it is KtPackageDirective ||
                    it is KtFileAnnotationList ||
                    // We do not consider comments before the package declarations as they are usually copyright notices.
                    (it is PsiComment && packageDeclaration != null && it.startOffset < packageDeclaration.startOffset)
        }
    }

    /**
     * We consider a class effectively empty if it only contains whitespaces.
     */
    private fun KtClassOrObject.isEffectivelyEmpty(): Boolean {
        if (declarations.isNotEmpty()) return false
        return body?.children?.all { it is PsiWhiteSpace } == true
    }

    /**
     * Deletes the [element] from its file but also deletes the containing declaration or file
     * if it can be deleted and is
     */
    private fun deleteMovedElement(element: KtNamedDeclaration) {
        val containingClass = element.containingClassOrObject
        element.deleteSingle()
        if (containingClass is KtObjectDeclaration && containingClass.isCompanion() && containingClass.isEffectivelyEmpty()) {
            containingClass.deleteSingle()
        }
    }

    private val conflicts: MultiMap<PsiElement, String> = MultiMap<PsiElement, String>()

    protected override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val usages = refUsages.get()
        ActionUtil.underModalProgress(
            operationDescriptor.project,
            RefactoringBundle.message("detecting.possible.conflicts")
        ) {
            operationDescriptor.moveDescriptors.forEach { moveDescriptor ->
                conflicts.putAllValues(
                    findAllMoveConflicts(
                        topLevelDeclarationsToMove = moveDescriptor.source.elements,
                        allDeclarationsToMove = operationDescriptor.sourceElements,
                        targetDir = moveDescriptor.target.baseDirectory,
                        targetPkg = moveDescriptor.target.pkgName,
                        target = moveDescriptor.target,
                        usages = usages
                            .filterIsInstance<MoveRenameUsageInfo>()
                            .filter { it.referencedElement.willBeMoved(operationDescriptor.sourceElements) },
                    )
                )
            }
        }
        return showConflicts(conflicts, usages)
    }

    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val movedElements = allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                operationDescriptor.moveDescriptors.flatMap { moveDescriptor ->
                    preprocessUsages(moveDescriptor.project, moveDescriptor.source, usages.toList())

                    val declarationsToMove = moveDescriptor.source.elements
                    val listeners = declarationsToMove.associateWith { transaction.getElementListener(it) }
                    declarationsToMove.forEach { elementToMove ->
                        preprocessDeclaration(elementToMove)
                        preDeclarationMoved(elementToMove)
                    }

                    val elementsToMove = declarationsToMove.withContext()
                    val sourceFiles = elementsToMove.map { it.containingFile as KtFile }.distinct()
                    val oldToNewMap = moveDescriptor.target.addElementsToTarget(elementsToMove, operationDescriptor.dirStructureMatchesPkg)
                    moveDescriptor.source.elements.forEach(::deleteMovedElement)
                    // Delete files if they are effectively empty after moving declarations out of them
                    sourceFiles.filter { it.isEffectivelyEmpty() }.forEach { it.delete() }

                    @Suppress("UNCHECKED_CAST")
                    retargetUsagesAfterMove(usages.toList(), oldToNewMap as Map<PsiElement, PsiElement>)
                    oldToNewMap.forEach { original, new ->
                        postprocessDeclaration(moveDescriptor.target, original, new)

                        val originalDeclaration = original as? KtNamedDeclaration
                        val newDeclaration = new as? KtNamedDeclaration
                        if (originalDeclaration != null && newDeclaration != null) {
                            postDeclarationMoved(original, new)
                        }
                        listeners[original]?.elementMoved(new)
                    }
                    oldToNewMap.values
                }
            }
        }

        ApplicationManager.getApplication().invokeLater {
            openFilesAfterMoving(movedElements)
        }
    }

    override fun performPsiSpoilingRefactoring() {
        operationDescriptor.moveCallBack?.refactoringCompleted()
    }

    override fun getBeforeData(): RefactoringEventData = RefactoringEventData().apply {
        addElements(operationDescriptor.sourceElements)
    }

    override fun doRun() {
        try {
            super.doRun()
        } finally {
            KotlinRefactoringListener.broadcastRefactoringExit(myProject, refactoringId)
        }
    }

    private val usedOuterNestedDeclarations = mutableMapOf<PsiElement, String?>()
    private fun KtNamedDeclaration.computeOrGetOuterInstanceName(): String? {
        if (this !is KtClassOrObject && this !is KtNamedFunction) return null
        if (usedOuterNestedDeclarations.containsKey(this)) {
            return usedOuterNestedDeclarations[this]
        }
        val nameToUse = if (isValid && usesOuterInstanceParameter()) {
            generateOuterInstanceParameterName(this)
        } else {
            null
        }
        usedOuterNestedDeclarations[this] = nameToUse
        return nameToUse
    }

    private fun findInternalOuterInstanceUsages(moveSource: K2MoveSourceDescriptor<*>): List<UsageInfo> {
        return moveSource.elements.filterIsInstance<KtNamedDeclaration>().flatMap { declaration ->
            collectOuterInstanceReferences(declaration)
        }
    }

    private fun PsiElement.willLoseOuterInstanceReference(): Boolean {
        if (this !is KtDeclaration) return false
        val containingClassOrObject = containingClassOrObject ?: return false
        // For properties, any outer instance reference is a conflict because we do not process them.
        val canReferenceOuterInstance = (this is KtFunction || this is KtClass) && computeOrGetOuterInstanceName() != null
        // For anything else, it is a conflict if it is contained in a class rather than an object
        return !canReferenceOuterInstance && containingClassOrObject !is KtObjectDeclaration
    }

    /**
     * Reports specific conflicts from the [allUsages].
     */
    private fun collectConflicts(
        allUsages: MutableSet<UsageInfo>
    ) {
        val usageIterator = allUsages.iterator()
        while (usageIterator.hasNext()) {
            val usage = usageIterator.next()
            val element = usage.element ?: continue

            val isConflict = when (usage) {
                is K2MoveRenameUsageInfo.Light -> {
                    val referencedElement = usage.upToDateReferencedElement as? KtNamedDeclaration
                    if (referencedElement?.containingClassOrObject != null &&
                        referencedElement.containingClassOrObject !is KtObjectDeclaration &&
                        referencedElement !is KtClassOrObject
                    ) {
                        // We only have the facility to correct outer class usages if the moved declaration is a nested class.
                        // If the containing class is an object we have no problem at all.
                        conflicts.putValue(
                            element,
                            KotlinBundle.message("usages.of.nested.declarations.from.non.kotlin.code.won.t.be.processed", element.text)
                        )
                        true
                    } else {
                        false
                    }
                }

                is OuterInstanceReferenceUsageInfo -> {
                    val upToDateMember = usage.upToDateMember
                    if (upToDateMember != null && upToDateMember.willLoseOuterInstanceReference()) {
                        conflicts.putValue(
                            element,
                            KotlinBundle.message(
                                "usages.of.outer.class.instance.inside.declaration.0.won.t.be.processed",
                                upToDateMember.nameAsSafeName.asString()
                            )
                        )
                        true
                    } else {
                        usage.reportConflictIfAny(conflicts)
                    }
                }

                else -> false
            }
            if (isConflict) {
                usageIterator.remove()
            }
        }
    }

    /**
     * Preprocesses the [usages] so that references can be adapted to work with the new code.
     */
    private fun preprocessUsages(
        project: Project,
        moveSource: K2MoveSourceDescriptor<*>,
        usages: List<UsageInfo>
    ) {
        val psiFactory = KtPsiFactory(project)
        for (declarationToMove in moveSource.elements) {
            if (declarationToMove !is KtNamedDeclaration) continue
            val outerInstanceParameterName = declarationToMove.computeOrGetOuterInstanceName() ?: continue

            for (usage in usages) {
                val newOuterInstanceRef = psiFactory.createExpression(quoteNameIfNeeded(outerInstanceParameterName))
                if (usage is MoveRenameUsageInfo) {
                    val referencedNestedDeclaration = usage.referencedElement?.unwrapped as? KtNamedDeclaration
                    if (referencedNestedDeclaration == declarationToMove) {
                        val outerClass = referencedNestedDeclaration.containingClassOrObject
                        val lightOuterClass = outerClass?.toLightClass()
                        if (lightOuterClass != null) {
                            // While this is called the `MoveInnerClassUsagesHandler`, it will also correctly modify usages
                            // of inner methods from Kotlin code (but not from Java code!).
                            MoveInnerClassUsagesHandler.EP_NAME
                                .forLanguage(usage.element?.language ?: continue)
                                ?.correctInnerClassUsage(usage, lightOuterClass, outerInstanceParameterName)
                        }
                    }
                }

                when (usage) {
                    is OuterInstanceReferenceUsageInfo.ExplicitThis -> {
                        usage.expression.replace(newOuterInstanceRef)
                    }

                    is OuterInstanceReferenceUsageInfo.ImplicitReceiver -> {
                        usage.callElement.let {
                            it.replace(
                                psiFactory.createExpressionByPattern(
                                    "$0.$1",
                                    quoteNameIfNeeded(outerInstanceParameterName),
                                    it
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun quoteNameIfNeeded(name: String): String {
        return if (name.isIdentifier()) {
            name
        } else {
            "`$name`"
        }
    }

    /**
     * This function is used to preprocess the [originalDeclaration] before the move happens.
     * For example, we add a parameter for the outer instance here before moving the declaration.
     */
    @OptIn(KaExperimentalApi::class)
    private fun preprocessDeclaration(
        originalDeclaration: KtNamedDeclaration
    ) {
        val containingClass = originalDeclaration.containingClassOrObject
        val psiFactory = KtPsiFactory(originalDeclaration.project)
        val outerInstanceParameterName = originalDeclaration.computeOrGetOuterInstanceName()
        with(originalDeclaration) {
            when (this) {
                is KtClass -> {
                    if (hasModifier(KtTokens.INNER_KEYWORD)) removeModifier(KtTokens.INNER_KEYWORD)
                    if (hasModifier(KtTokens.PROTECTED_KEYWORD)) removeModifier(KtTokens.PROTECTED_KEYWORD)

                    if (outerInstanceParameterName != null) {
                        val containingClass = containingClassOrObject ?: return
                        analyze(originalDeclaration) {
                            // Use the fully qualified type because we have not moved it to the new location yet
                            val type = containingClass.classSymbol?.defaultType?.render(
                                renderer = KaTypeRendererForSource.WITH_QUALIFIED_NAMES,
                                position = Variance.INVARIANT
                            ) ?: return
                            val possiblyQuotedName = quoteNameIfNeeded(outerInstanceParameterName)
                            val parameter = KtPsiFactory(project).createParameter("private val $possiblyQuotedName: $type")
                            val constructorParameterList = createPrimaryConstructorParameterListIfAbsent()
                            constructorParameterList.addParameterBefore(parameter, constructorParameterList.parameters.firstOrNull())
                        }
                    }
                }

                is KtNamedFunction -> {
                    if (outerInstanceParameterName != null) {
                        val outerInstanceType = analyze(originalDeclaration) {
                            val type = (containingClass?.symbol as? KaClassSymbol)?.defaultType ?: return@analyze null
                            type.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, Variance.INVARIANT)
                        }
                        if (outerInstanceType == null) return
                        val possiblyQuotedName = quoteNameIfNeeded(outerInstanceParameterName)
                        valueParameterList?.addParameterBefore(
                            psiFactory.createParameter(
                                "${possiblyQuotedName}: $outerInstanceType"
                            ),
                            valueParameterList?.parameters?.firstOrNull()
                        )
                    }
                }

                is KtProperty -> {

                }
            }
        }
    }

    /**
     * This function is used to postprocess the [newDeclaration] after the move has happened.
     * For example, we can remove modifiers that should be removed after moving.
     */
    private fun postprocessDeclaration(
        moveTarget: K2MoveTargetDescriptor,
        originalDeclaration: PsiElement,
        newDeclaration: PsiElement
    ) {
        val outerInstanceParameterName = (originalDeclaration as? KtNamedDeclaration)?.computeOrGetOuterInstanceName()
        when (newDeclaration) {
            is KtClass -> {
                val primaryConstructor = newDeclaration.primaryConstructor ?: return
                val addedParameter = primaryConstructor.valueParameters.firstOrNull { it.name == outerInstanceParameterName } ?: return
                shortenReferences(addedParameter)
            }

            is KtNamedFunction -> {
                if (outerInstanceParameterName != null) {
                    val addedParameter = newDeclaration.valueParameterList?.parameters?.firstOrNull() ?: return
                    shortenReferences(addedParameter)
                }
                val moveTargetType = (moveTarget as? Declaration<*>)?.getTargetType()
                if (moveTargetType == DeclarationTargetType.COMPANION_OBJECT || moveTargetType == DeclarationTargetType.OBJECT) {
                    newDeclaration.removeModifier(KtTokens.OPEN_KEYWORD)
                    newDeclaration.removeModifier(KtTokens.FINAL_KEYWORD)
                    newDeclaration.reformatted(true)
                }
            }
        }
    }

    /**
     * This method attempts to find a name for the outer instance
     * based on the outer classes name and avoids name clashes by attempting to
     * choose a name that is not already taken anywhere in the declaration.
     */
    private fun generateOuterInstanceParameterName(declaration: KtNamedDeclaration): String? {
        val suggestedName = declaration.containingClass()?.takeIf {
            (declaration !is KtClass || declaration.isInner()) && declaration !is KtProperty
        }?.name?.decapitalizeAsciiOnly() ?: return null

        val usedNames = mutableSetOf<String>()
        declaration.accept(object : KtTreeVisitorVoid() {
            override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                super.visitNamedDeclaration(declaration)
                usedNames.add(declaration.nameAsSafeName.asString())
            }

            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                super.visitSimpleNameExpression(expression)
                expression.getIdentifier()?.text?.let { usedNames.add(it) }
            }
        })

        if (suggestedName !in usedNames) return suggestedName
        for (i in 1..1000) {
            val nameWithNum = "$suggestedName$i"
            if (nameWithNum !in usedNames) return nameWithNum
        }
        return null
    }

    /**
     * Invoked before the [declaration] is moved to its target.
     * Implementations can override this method to modify the [declaration] before it is moved.
     */
    open fun preDeclarationMoved(declaration: KtNamedDeclaration) {}

    /**
     * Invoked after the [originalDeclaration] is moved to its target and changed to be the [newDeclaration].
     * Implementations can override this method to modify the [newDeclaration] after it is moved.
     * Note: The [originalDeclaration] is usually not valid anymore at this point because it was moved.
     */
    open fun postDeclarationMoved(originalDeclaration: KtNamedDeclaration, newDeclaration: KtNamedDeclaration) {}

    /**
     * Called after the move is completed to open the [movedElements] in the editor in their respective new files.
     * Can be overridden to not open the files or to invoke other operations like rename.
     * Note: this is invoked in an `invokeLater` so it does not happen immediately after the move is completed.
     */
    open fun openFilesAfterMoving(movedElements: List<KtNamedDeclaration>) {
        if (MoveFilesOrDirectoriesDialog.isOpenInEditorProperty()) { // for simplicity, we re-use logic from move files
            EditorHelper.openFilesInEditor(movedElements.toTypedArray())
        }
    }
}