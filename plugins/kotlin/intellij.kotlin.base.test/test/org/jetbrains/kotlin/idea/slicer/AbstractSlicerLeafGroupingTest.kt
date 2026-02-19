// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.slicer

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.slicer.SliceLanguageSupportProvider
import com.intellij.slicer.SliceLeafAnalyzer
import com.intellij.slicer.SliceRootNode
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File

abstract class AbstractSlicerLeafGroupingTest : AbstractSlicerTest() {
    protected abstract fun createAnalyzer(sliceProvider: SliceLanguageSupportProvider): SliceLeafAnalyzer

    override fun doTest(path: String, sliceProvider: SliceLanguageSupportProvider, rootNode: SliceRootNode) {
        val renderedForest = ActionUtil.underModalProgress(project, "") {
            val treeStructure = TestSliceTreeStructure(rootNode)
            val analyzer = createAnalyzer(sliceProvider)
            val possibleElementsByNode = analyzer.createMap()
            val leafExpressions = analyzer.calcLeafExpressions(rootNode, treeStructure, possibleElementsByNode)
            val newRootNode = analyzer.createTreeGroupedByValues(leafExpressions, rootNode, possibleElementsByNode)
            buildString {
                newRootNode.children.map { groupRootNode -> buildTreeRepresentation(groupRootNode) }.sorted().forEach {
                    this.append(it)
                    this.append("\n")
                }
            }
        }
        KotlinTestUtils.assertEqualsToFile(getResultFile(path), renderedForest)
    }

    protected open fun getResultFile(path: String): File = File(path.replace(".kt", ".leafGroups.txt"))
}