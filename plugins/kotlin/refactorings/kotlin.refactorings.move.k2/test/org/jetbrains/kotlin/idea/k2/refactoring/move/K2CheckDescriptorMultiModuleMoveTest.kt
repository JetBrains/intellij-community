// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
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

    private fun checkNoMoveOutsideSourceRoot(moveOperationDescriptor: K2MoveOperationDescriptor<*>) {
        moveOperationDescriptor.moveDescriptors.forEach { descriptor ->
            val element = descriptor.source.elements.singleOrNull() ?: error("Single source element to move is expected")
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

    private fun setAllMoveSettingsOn(moveModel: K2MoveModel) {
        setMoveModelSetting(moveModel.mppDeclarations, true)
        setMoveModelSetting(moveModel.searchReferences, true)
        setMoveModelSetting(moveModel.searchInComments, true)
        setMoveModelSetting(moveModel.searchForText, true)
    }
}
