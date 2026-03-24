// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.K2MoveModel
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.K2MoveTargetModel
import org.jetbrains.kotlin.idea.util.sourceRoot
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

/**
 * This test is **NOT** auto-generated to allow move model configuration with move descriptor checks after conversion.
 * Most of the other generated tests set up descriptors from the test data directly, which doesn't allow checking the conversion.
 */
@TestDataPath($$"$CONTENT_ROOT")
@RunWith(JUnit38ClassRunner::class)
@TestRoot("refactorings/kotlin.refactorings.move.k2")
@TestMetadata("../../idea/tests/testData/refactoring/moveDescriptors")
class K2CheckDescriptorMultiModuleMoveTest : AbstractK2CheckDescriptorMultiModuleMoveTest() {
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    @TestMetadata("moveExpectProperty")
    @Throws(Exception::class)
    fun testMoveExpectProperty() {
        doTest(
            "moveExpectProperty/moveExpectProperty.test",
            configureMoveModel = { moveModel ->
                setAllMoveSettingsOn(moveModel)
                setNewTargetPackageInSameRoot(moveModel, FqName("bar"))
            },
            checkMoveDescriptor = { moveOperationDescriptor -> checkNoMoveOutsideSourceRoot(moveOperationDescriptor) },
        )
    }

    @TestMetadata("moveActualFunction")
    @Throws(Exception::class)
    fun testMoveActualFunction() {
        doTest(
            "moveActualFunction/moveActualFunction.test",
            configureMoveModel = { moveModel ->
                setAllMoveSettingsOn(moveModel)
                setNewTargetPackageInSameRoot(moveModel, FqName("bar"))
            },
            checkMoveDescriptor = { moveOperationDescriptor -> checkNoMoveOutsideSourceRoot(moveOperationDescriptor) },
        )
    }

    @TestMetadata("moveActualAndRegularFunctionConventionalSourceSetNames")
    @Throws(Exception::class)
    fun testMoveActualAndRegularFunctionConventionalSourceSetNames() {
        doTest(
            "moveActualAndRegularFunctionConventionalSourceSetNames/moveActualAndRegularFunctionConventionalSourceSetNames.test",
            configureMoveModel = { moveModel ->
                setAllMoveSettingsOn(moveModel)
                setNewTargetPackageInSameRoot(moveModel, FqName("bar"))
                setTargetFile(moveModel, "target.kt")
            },
            checkMoveDescriptor = { moveOperationDescriptor -> checkNoMoveOutsideSourceRoot(moveOperationDescriptor) },
        )
    }

    @TestMetadata("moveActualClass")
    @Throws(Exception::class)
    fun testMoveActualClass() {
        doTest(
            "moveActualClass/moveActualClass.test",
            configureMoveModel = { moveModel ->
                setAllMoveSettingsOn(moveModel)
                setNewTargetPackageInSameRoot(moveModel, FqName("bar"))
            },
            checkMoveDescriptor = { moveOperationDescriptor -> checkNoMoveOutsideSourceRoot(moveOperationDescriptor) },
        )
    }

    @TestMetadata("moveExpectFunctionToExistingPackage")
    @Throws(Exception::class)
    fun testMoveExpectFunctionToExistingPackage() {
        doTest(
            "moveExpectFunctionToExistingPackage/moveExpectFunctionToExistingPackage.test",
            configureMoveModel = { moveModel ->
                setAllMoveSettingsOn(moveModel)
                setExistingTargetPackageInSameRoot(moveModel, FqName("bar"))
            },
            checkMoveDescriptor = { moveOperationDescriptor -> checkNoMoveOutsideSourceRoot(moveOperationDescriptor) },
        )
    }

    @TestMetadata("moveTheOnlyDeclarationInFile")
    @Throws(Exception::class)
    fun testMoveTheOnlyDeclarationInFile() {
        doTest(
            "moveTheOnlyDeclarationInFile/moveTheOnlyDeclarationInFile.test",
            configureMoveModel = { moveModel ->
                setAllMoveSettingsOn(moveModel)
                setMoveModelSetting(moveModel.mppDeclarations, false)
                setNewTargetPackageInSameRoot(moveModel, FqName("bar"))
            },
            checkMoveDescriptor = {
                assert(isFileMove(it)) { "File move was expected, but found ${it::class.java.canonicalName}" }
            },
        )
    }

    @TestMetadata("moveTheOnlyDeclarationInFileChangeFileName")
    @Throws(Exception::class)
    fun testMoveTheOnlyDeclarationInFileChangeFileName() {
        doTest(
            "moveTheOnlyDeclarationInFileChangeFileName/moveTheOnlyDeclarationInFileChangeFileName.test",
            configureMoveModel = { moveModel ->
                setAllMoveSettingsOn(moveModel)
                setMoveModelSetting(moveModel.mppDeclarations, false)
                setNewTargetPackageInSameRoot(moveModel, FqName("bar"))
                setTargetFile(moveModel, "CustomName.kt")
            },
            checkMoveDescriptor = {
                assert(!isFileMove(it)) { "File move should not be done if the file name is changed" }
            },
        )
    }

    @TestMetadata("moveTheOnlyDeclarationInFileWithKmpEnabled")
    @Throws(Exception::class)
    fun testMoveTheOnlyDeclarationInFileWithKmpEnabled() {
        doTest(
            "moveTheOnlyDeclarationInFileWithKmpEnabled/moveTheOnlyDeclarationInFileWithKmpEnabled.test",
            configureMoveModel = { moveModel ->
                setAllMoveSettingsOn(moveModel)
                setMoveModelSetting(moveModel.mppDeclarations, true)
                setNewTargetPackageInSameRoot(moveModel, FqName("bar"))
            },
            checkMoveDescriptor = {
                assert(!isFileMove(it)) { "File move should not be done with KMP setting enabled" }
            },
        )
    }

    @TestMetadata("moveTheOnlyDeclarationToExistingFile")
    @Throws(Exception::class)
    fun testMoveTheOnlyDeclarationToExistingFile() {
        doTest(
            "moveTheOnlyDeclarationToExistingFile/moveTheOnlyDeclarationToExistingFile.test",
            configureMoveModel = { moveModel ->
                setAllMoveSettingsOn(moveModel)
                setMoveModelSetting(moveModel.mppDeclarations, false)
                setExistingTargetPackageInSameRoot(moveModel, FqName("bar"))
            },
            checkMoveDescriptor = {
                assert(!isFileMove(it)) { "File move should not be done when the target file exists" }
            },
        )
    }

    @TestMetadata("moveOneOfTwoDeclarationsInFile")
    @Throws(Exception::class)
    fun testMoveOneOfTwoDeclarationsInFile() {
        doTest(
            "moveOneOfTwoDeclarationsInFile/moveOneOfTwoDeclarationsInFile.test",
            configureMoveModel = { moveModel ->
                setAllMoveSettingsOn(moveModel)
                setMoveModelSetting(moveModel.mppDeclarations, false)
                setNewTargetPackageInSameRoot(moveModel, FqName("bar"))
            },
            checkMoveDescriptor = {
                assert(!isFileMove(it)) { "Unexpected file move" }
            },
        )
    }

    @TestMetadata("moveTwoOfTwoDeclarationsInFile")
    @Throws(Exception::class)
    fun testMoveTwoOfTwoDeclarationsInFile() {
        doTest(
            "moveTwoOfTwoDeclarationsInFile/moveTwoOfTwoDeclarationsInFile.test",
            configureMoveModel = { moveModel ->
                setAllMoveSettingsOn(moveModel)
                setMoveModelSetting(moveModel.mppDeclarations, false)
                setNewTargetPackageInSameRoot(moveModel, FqName("bar"))
            },
            checkMoveDescriptor = {
                assert(isFileMove(it)) { "File move was expected, but found ${it::class.java.canonicalName}" }
            },
        )
    }

    /**
     * Set the package and reset the target directory to the source root in the move model.
     * Mimics the UI dialog behavior when a non-existent package is selected.
     */
    private fun setNewTargetPackageInSameRoot(moveModel: K2MoveModel, packageFqName: FqName) {
        val moveTargetModel = moveModel.target
        val targetModel = moveTargetModel as? K2MoveTargetModel.SourceDirectoryChooser
            ?: error("A move target with selectable directory is expected")
        targetModel.pkgName = packageFqName
        targetModel.directory = targetModel.directory.sourceRoot?.toPsiDirectory(project)
            ?: error("Can't find source root for module ${targetModel.directory.module?.name}")
    }

    /**
     * Set the package and the target directory in the move model.
     * The physical directory should exist for the package, otherwise [setNewTargetPackageInSameRoot] should be used.
     */
    private fun setExistingTargetPackageInSameRoot(moveModel: K2MoveModel, packageFqName: FqName) {
        val moveTargetModel = moveModel.target
        val targetModel = moveTargetModel as? K2MoveTargetModel.SourceDirectoryChooser
            ?: error("A move target with selectable directory is expected")
        targetModel.pkgName = packageFqName
        val sourceRootDir = targetModel.directory.sourceRoot?.toPsiDirectory(project) ?: error("Can't find source root")
        val packageSubdirectory = packageFqName.pathSegments().fold(sourceRootDir) { dir, segment ->
            dir.findSubdirectory(packageFqName.asString())
                ?: error("'$segment' doesn't exist in '${dir.name}'. " +
                                 "The directories should exist for the package '${packageFqName.asString()}' " +
                                 "in the source root ${sourceRootDir.virtualFile.path}.'")
        }
        targetModel.directory = packageSubdirectory
    }

    /**
     * Imitates target file change through the file chooser.
     */
    private fun setTargetFile(moveModel: K2MoveModel, fileName: String) {
        val moveTarget = moveModel.target
        if (moveTarget !is K2MoveTargetModel.FileChooser)
            throw AssertionError("Unexpected move target model: ${moveTarget::class.simpleName}")
        moveTarget.fileName = fileName
    }

    private fun checkNoMoveOutsideSourceRoot(moveOperationDescriptor: K2MoveOperationDescriptor<*>) {
        moveOperationDescriptor.moveDescriptors.forEach { descriptor ->
            descriptor.source.elements.forEach { element ->
                val elementSourceDir = element.containingFile.sourceRoot ?: error("Can't find source root for element")
                val targetRoot = descriptor.target.baseDirectory.sourceRoot ?: error("Can't find source root for target")
                assert(elementSourceDir == targetRoot) {
                    """Element was unexpectedly moved outside of its source root:
                            |${element.text}
                            |Element root: ${elementSourceDir.path}
                            |Target root: ${targetRoot.path}
                            |""".trimMargin()
                }
            }
        }
    }

    /**
     * Checks whether the move descriptor is a file move.
     */
    private fun isFileMove(moveOperationDescriptor: K2MoveOperationDescriptor<*>): Boolean =
        moveOperationDescriptor.moveDescriptors.singleOrNull { it is K2MoveDescriptor.Files } != null

    private fun setAllMoveSettingsOn(moveModel: K2MoveModel) {
        setMoveModelSetting(moveModel.mppDeclarations, true)
        setMoveModelSetting(moveModel.searchReferences, true)
        setMoveModelSetting(moveModel.searchInComments, true)
        setMoveModelSetting(moveModel.searchForText, true)
    }
}
