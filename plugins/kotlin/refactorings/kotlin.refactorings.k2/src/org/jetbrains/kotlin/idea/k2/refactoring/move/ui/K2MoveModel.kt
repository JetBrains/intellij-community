// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.parentOfTypes
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.plus
import com.intellij.ui.dsl.builder.selected
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefixOrRoot
import org.jetbrains.kotlin.idea.core.getPackage
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor.Declarations
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.K2MoveTargetModel.Declarations.MoveTargetType
import org.jetbrains.kotlin.idea.search.ExpectActualUtils
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

/**
 * @see K2MoveDescriptor
 */
sealed class K2MoveModel(private val observableUiSettings: ObservableUiSettings) : K2MoveModelObservableSettings {
    init {
        observableUiSettings.registerK2MoveModelSettings(this)
    }

    abstract val project: Project

    abstract val source: K2MoveSourceModel<*>

    abstract val target: K2MoveTargetModel

    abstract val inSourceRoot: Boolean

    abstract val moveCallBack: MoveCallback?

    @RequiresReadLock
    abstract fun toDescriptor(): K2MoveOperationDescriptor<*>

    /**
     * Returns whether running the refactoring is meaningful.
     * For example, moving a file to the package and directory it's already in is not meaningful.
     */
    open fun isValidRefactoring(): Boolean {
        return source.elements.isNotEmpty()
    }

    open fun buildPanel(panel: Panel): Unit = with(panel) {
        val mppDeclarationSelectedObservable = observableUiSettings.mppDeclarationsSelected.transform { isSelected ->
            isSelected && inSourceRoot
        }

        row {
            panel {
                createSettingComboBox(searchForText)
                createSettingComboBox(searchReferences, inSourceRoot)
            }.align(AlignY.TOP + AlignX.LEFT)
            panel {
                createSettingComboBox(searchInComments)
                createSettingComboBox(
                    mppDeclarations, enabled = mppDeclarationSelectedObservable,
                    visible = mppDeclarationSelectedObservable,
                )
            }.align(AlignY.TOP + AlignX.RIGHT)
        }
    }

    override val mppDeclarationsSetting: ObservableProperty<Boolean>
        get() = mppDeclarations.observableProperty

    internal val searchForText: BoundMoveModelSetting = BoundMoveModelSetting(MoveModelSetting.SEARCH_FOR_TEXT)

    internal val searchInComments: BoundMoveModelSetting = BoundMoveModelSetting(MoveModelSetting.SEARCH_IN_COMMENTS)

    internal val searchReferences: BoundMoveModelSetting = BoundMoveModelSetting(MoveModelSetting.SEARCH_REFERENCES)

    internal val mppDeclarations: BoundMoveModelSetting = BoundMoveModelSetting(MoveModelSetting.MPP_DECLARATIONS)

    private fun Panel.createSettingComboBox(
        boundSetting: BoundMoveModelSetting,
        enabled: Boolean = true,
        visible: Boolean = true,
    ) {
        createSettingComboBox(
            boundSetting = boundSetting,
            enabled = ConstantBooleanObservableProperty(enabled),
            visible = ConstantBooleanObservableProperty(visible),
        )
    }

    private fun Panel.createSettingComboBox(
        boundSetting: BoundMoveModelSetting,
        enabled: ObservableProperty<Boolean>,
        visible: ObservableProperty<Boolean>,
    ) {
        row {
            val checkBox = checkBox(boundSetting.text).enabledIf(enabled).visibleIf(visible)
            checkBox.onChanged { boundSetting.observableProperty.set(it.isSelected) }
            // reset only is intentional
            enabled.afterChange { isEnabled -> if (!isEnabled) checkBox.selected(false) }
            checkBox.bindSelected(boundSetting::state)
            // value set in bindSelected doesn't trigger listeners
            val initial = enabled.get() && boundSetting.state
            checkBox.selected(initial)
            boundSetting.observableProperty.set(initial)
        }.layout(RowLayout.PARENT_GRID)
    }

    /**
     * @see K2MoveDescriptor.Files
     */
    class Files(
        override val project: Project,
        override val source: K2MoveSourceModel.FileSource,
        override val target: K2MoveTargetModel.SourceDirectory,
        override val inSourceRoot: Boolean,
        observableUiSettings: ObservableUiSettings,
        override val moveCallBack: MoveCallback? = null,
    ) : K2MoveModel(observableUiSettings) {
        private fun PsiFile.isAlreadyInTarget(): Boolean {
            return parent == target.directory && when (this) {
                is PsiJavaFile -> packageName == target.pkgName.asString()
                is KtFile -> packageFqName == target.pkgName && !target.isMoveToExplicitPackage()
                else -> true
            }
        }

        override fun isValidRefactoring(): Boolean {
            if (!super.isValidRefactoring()) return false
            if (source.elements.all { it is PsiFile && it.isAlreadyInTarget() }) return false
            return true
        }

        override fun toDescriptor(): K2MoveOperationDescriptor.Files {
            val srcDescr = source.toDescriptor()
            val targetDescr = target.toDescriptor()
            val searchReferences = if (inSourceRoot) searchReferences.state else false
            val moveDescriptor = K2MoveDescriptor.Files(
                project,
                srcDescr,
                targetDescr
            )
            val operationDescriptor = K2MoveOperationDescriptor.Files(
                project = project,
                moveDescriptors = listOf(moveDescriptor),
                searchForText = searchForText.state,
                searchReferences = searchReferences,
                searchInComments = searchInComments.state,
                dirStructureMatchesPkg = true,
                isMoveToExplicitPackage = target.isMoveToExplicitPackage(),
                moveCallBack = moveCallBack,
            )
            return operationDescriptor
        }
    }

    /**
     * @see K2MoveDescriptor.Declarations
     */
    open class Declarations(
        override val project: Project,
        override val source: K2MoveSourceModel.ElementSource,
        override val target: K2MoveTargetModel,
        override val inSourceRoot: Boolean,
        observableUiSettings: ObservableUiSettings,
        override val moveCallBack: MoveCallback? = null
    ) : K2MoveModel(observableUiSettings) {
        private fun isValidFileRefactoring(fileName: String): Boolean {
            fun KtFile.isTargetFile(): Boolean {
                return containingDirectory == target.directory
                        && packageFqName == target.pkgName
                        && name == fileName
            }
            if (!fileName.isValidKotlinFile()) return false
            val files = source.elements.map { it.containingFile }.toSet()
            return files.size != 1 || !(files.single() as KtFile).isTargetFile() || target.isMoveToExplicitPackage()
        }

        private fun K2MoveTargetModel.Declarations.isValidRefactoring(): Boolean {
            return if (destinationTargetType == MoveTargetType.FILE) {
                isValidFileRefactoring(fileName)
            } else {
                val destinationClass = destinationClass ?: return false
                // Cannot allow moving the class into itself
                destinationClass.parentsWithSelf.none { it in source.elements }
            }
        }

        internal fun isValidDeclarationsRefactoring(): Boolean {
            val target = target
            return when (target) {
                is K2MoveTargetModel.Declarations -> target.isValidRefactoring()
                is K2MoveTargetModel.File -> isValidFileRefactoring(target.fileName)
                else -> false
            }
        }

        private fun shouldMoveDeclarationsAsFile(source: K2MoveSourceModel.ElementSource, target: K2MoveTargetModel): Boolean {
            val declarations = source.elements
            val singleSourceFileOrNull = declarations.map { it.containingKtFile }.toSet().singleOrNull()
            val allDeclarationsInFileAreMoved = singleSourceFileOrNull?.let { singleFile ->
                singleFile.declarations.all { it in declarations }
            } ?: false
            val targetFileName = (target as? K2MoveTargetModel.FileChooser)?.fileName
            val fileNamesMatch = targetFileName == singleSourceFileOrNull?.name
            val targetFileExists = (target as? K2MoveTargetModel.FileChooser)?.directory?.files?.find { it.name == targetFileName } != null

            return !mppDeclarations.state && allDeclarationsInFileAreMoved && fileNamesMatch && !targetFileExists
        }

        override fun isValidRefactoring(): Boolean {
            return super.isValidRefactoring() && isValidDeclarationsRefactoring()
        }

        /**
         * Creates a move descriptor from a move model for declarations.
         *
         * — If the move is non-KMP and all declarations from a single file are selected without the file name being changed,
         *   [K2MoveOperationDescriptor.Files] move is used instead of the move for declarations to better preserve VCS history.
         *
         * — If the KMP move option is enabled and some declarations are `expect` or `actual`,
         *   a descriptor for multiplatform move is created. KMP declarations are moved in their
         *   corresponding source sets via a set of move descriptors.
         *
         * — Otherwise, a regular move for declarations with a single descriptor is created.
         */
        override fun toDescriptor(): K2MoveOperationDescriptor<*> {
            val declarations = source.elements
            val searchForReferences = if (inSourceRoot) searchReferences.state else false
            val searchForText = searchForText.state
            val searchInComments = searchInComments.state

            if (shouldMoveDeclarationsAsFile(source, target)) {
                val moveDescriptor = K2MoveDescriptor.Files(
                    project = project,
                    source = K2MoveSourceDescriptor.FileSource(listOf(declarations.first().containingFile)),
                    target = K2MoveTargetDescriptor.Directory(target.pkgName, target.directory, target.isMoveToExplicitPackage()),
                )

                return K2MoveOperationDescriptor.Files(
                    project = project,
                    moveDescriptors = listOf(moveDescriptor),
                    searchForText = searchForText,
                    searchInComments = searchInComments,
                    searchReferences = searchForReferences,
                    dirStructureMatchesPkg = true,
                    isMoveToExplicitPackage = target.isMoveToExplicitPackage(),
                    moveCallBack = moveCallBack,
                )
            } else if (mppDeclarations.state && declarations.any { it.isExpectOrActual() }) {
                val descriptors = declarations.flatMap { elem ->
                    ExpectActualUtils.withExpectedActuals(elem).filterIsInstance<KtNamedDeclaration>()
                }.groupBy { elem ->
                    ProjectFileIndex.getInstance(project).getSourceRootForFile(elem.containingFile.virtualFile)?.toPsiDirectory(project)
                }.mapNotNull { (baseDir, elements) ->
                    if (baseDir == null) return@mapNotNull null
                    val srcDescriptor = K2MoveSourceDescriptor.ElementSource(elements)
                    val targetDescriptor = target.toDescriptor(kmpSourceRoot = baseDir) as K2MoveTargetDescriptor.Declaration<*>
                    K2MoveDescriptor.Declarations(project, srcDescriptor, targetDescriptor)
                }
                return Declarations(
                    project = project,
                    moveDescriptors = descriptors,
                    searchForText = searchForText,
                    searchInComments = searchInComments,
                    searchReferences = searchForReferences,
                    dirStructureMatchesPkg = true,
                    moveCallBack = moveCallBack
                )
            } else {
                val srcDescr = K2MoveSourceDescriptor.ElementSource(declarations)
                val targetDescr = target.toDescriptor() as K2MoveTargetDescriptor.Declaration<*>
                val moveDescriptor = K2MoveDescriptor.Declarations(project, srcDescr, targetDescr)
                return Declarations(
                    project = project,
                    moveDescriptors = listOf(moveDescriptor),
                    searchForText = searchForText,
                    searchInComments = searchInComments,
                    searchReferences = searchForReferences,
                    dirStructureMatchesPkg = true,
                    moveCallBack = moveCallBack
                )
            }
        }
    }

    companion object {
        fun create(
            elements: Array<out PsiElement>,
            targetContainer: PsiElement?,
            editor: Editor? = null,
            moveCallBack: MoveCallback? = null
        ): K2MoveModel? {
            val project = elements.firstOrNull()?.project ?: error("Elements not part of project")

            val elementsToMove = elements.filter { elem ->
                when (elem) {
                    is PsiDirectory, is PsiFile -> true
                    else -> {
                        val container = elem.parentOfTypes(PsiFile::class, KtNamedDeclaration::class) ?: error("Element not in Kotlin file")
                        container !in elements
                    }
                }
            }

            if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, elements.toList(), true)) return null

            for (moveCheck in k2MoveModelChecks) {
                if (!moveCheck.isMoveAllowed(elementsToMove, targetContainer)) {
                    moveCheck.showErrorHint(project, editor)
                    return null
                }
            }

            val declarationsFromFiles = elementsToMove.flatMap { elem ->
                when (elem) {
                    is KtFile -> elem.declarations.filterIsInstance<KtNamedDeclaration>()
                    is KtNamedDeclaration -> listOf(elem)
                    else -> listOf()
                }
            }
            val inSourceRoot = isInSourceRoot(project, elementsToMove, targetContainer)
            val explicitPkgMoveFqName = findExplicitPkgMoveFqName(elementsToMove)
            val observableUiSettings = ObservableUiSettingsImpl()

            return when {
                (elementsToMove.all { it is KtFile } && targetContainer is PsiDirectory)
                        || isMultiFileMove(elementsToMove)
                        || declarationsFromFiles.isEmpty()
                        || (targetContainer is PsiDirectory && targetContainer.getPackage() == null) -> {
                    // this move can contain foreign language files
                    val source = K2MoveSourceModel.FileSource(elementsToMove.toFileElements().toSet(), observableUiSettings)
                    val target = if (targetContainer is PsiDirectory) {
                        val pkg = targetContainer.getFqNameWithImplicitPrefixOrRoot()
                        K2MoveTargetModel.SourceDirectory(
                            pkgName = pkg,
                            directory = targetContainer,
                            explicitPkgMoveFqName = explicitPkgMoveFqName,
                            observableUiSettings = observableUiSettings,
                        )
                    } else { // no default target is provided, happens when invoking refactoring via keyboard instead of drag-and-drop
                        val file = elementsToMove.firstOrNull { it.containingFile != null }?.containingFile
                            ?: error("No default target found")
                        val directory = file.containingDirectory ?: error("No default target found")
                        val pkgName = elementsToMove.firstIsInstanceOrNull<KtElement>()?.containingKtFile?.packageFqName ?: FqName.ROOT
                        K2MoveTargetModel.SourceDirectory(pkgName, directory, explicitPkgMoveFqName = null, observableUiSettings)
                    }
                    Files(project, source, target, inSourceRoot, observableUiSettings, moveCallBack)
                }

                targetContainer is KtFile || targetContainer.isSingleClassContainer() || isSingleFileMove(elementsToMove) -> {
                    val source = K2MoveSourceModel.ElementSource(declarationsFromFiles.toSet(), observableUiSettings)
                    val targetFile = targetContainer?.containingFile
                    val target = if (targetFile is KtFile) {
                        K2MoveTargetModel.File(targetFile, observableUiSettings)
                    } else if (targetContainer is PsiDirectory) {
                        val pkg = targetContainer.getFqNameWithImplicitPrefixOrRoot()
                        K2MoveTargetModel.File(
                            fileName = findSourceFileNameByMovedElements(elementsToMove),
                            pkg = pkg,
                            directory = targetContainer,
                            explicitPkgMoveFqName = explicitPkgMoveFqName,
                            observableUiSettings = observableUiSettings,
                        )
                    } else { // no default target is provided, happens when invoking refactoring via keyboard instead of drag-and-drop
                        val firstElem = elementsToMove.firstOrNull() as KtElement
                        val containingFile = firstElem.containingKtFile
                        val psiDirectory = containingFile.containingDirectory ?: error("No directory found")
                        K2MoveTargetModel.Declarations(
                            defaultDirectory = psiDirectory,
                            defaultPkgName = containingFile.packageFqName,
                            defaultFileName = findSourceFileNameByMovedElements(elementsToMove),
                            observableUiSettings = observableUiSettings,
                        )
                    }

                    Declarations(
                        project = project,
                        source = source,
                        target = target,
                        inSourceRoot = inSourceRoot,
                        observableUiSettings = observableUiSettings,
                        moveCallBack = moveCallBack
                    )
                }

                else -> error("Unsupported move operation")
            }
        }
    }
}