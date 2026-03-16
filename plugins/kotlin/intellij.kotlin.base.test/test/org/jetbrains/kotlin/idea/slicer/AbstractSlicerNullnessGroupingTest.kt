// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.slicer

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.slicer.SliceLanguageSupportProvider
import com.intellij.slicer.SliceRootNode
import org.jetbrains.kotlin.idea.codeInsight.slicer.HackedSliceNullnessAnalyzerBase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File

abstract class AbstractSlicerNullnessGroupingTest : AbstractSlicerTest() {
    protected abstract fun createNullnessAnalyzer(sliceProvider: SliceLanguageSupportProvider): HackedSliceNullnessAnalyzerBase

    override fun doTest(path: String, sliceProvider: SliceLanguageSupportProvider, rootNode: SliceRootNode) {
        val renderedForest = ActionUtil.underModalProgress(project, "") {
            val treeStructure = TestSliceTreeStructure(rootNode)
            val analyzer = createNullnessAnalyzer(sliceProvider)
            val nullnessByNode = HackedSliceNullnessAnalyzerBase.createMap()
            val nullness = analyzer.calcNullableLeaves(rootNode, treeStructure, nullnessByNode)
            val newRootNode = analyzer.createNewTree(nullness, rootNode, nullnessByNode)
            buildString {
                for (groupRootNode in newRootNode.children) {
                    append(buildTreeRepresentation(groupRootNode))
                    append("\n")
                }
            }
        }
        KotlinTestUtils.assertEqualsToFile(getResultsFile(path), renderedForest)
    }

    protected open fun getResultsFile(path: String): File = File(path.replace(".kt", ".nullnessGroups.txt"))

    override fun supportIgnoring(): Boolean = false
}