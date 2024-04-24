// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.testGenerator.model

import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import java.io.File

interface TGroup {
    val modulePath: String

    val testSourcesPath: String
    val testDataPath: String
    val category: GroupCategory

    val kotlinRoot: File
    val moduleRoot: File
    val testSourcesRoot: File
    val testDataRoot: File

    val suites: List<TSuite>

    val isCompilerTestData: Boolean
}

interface MutableTGroup : TGroup {
    override val suites: MutableList<TSuite>
}

class TGroupImpl(
    override val modulePath: String,
    override val testSourcesPath: String,
    override val testDataPath: String,
    override val category: GroupCategory,
    override val isCompilerTestData: Boolean,
) : MutableTGroup {
    override val kotlinRoot = KotlinRoot.DIR
    override val moduleRoot = File(kotlinRoot, modulePath)
    override val testSourcesRoot = File(moduleRoot, testSourcesPath)
    override val testDataRoot = File(testDataPath).takeIf { it.isAbsolute } ?: File(moduleRoot, testDataPath)
    override val suites = mutableListOf<TSuite>()
}

enum class GroupCategory {
    UNCATEGORIZED,
    HIGHLIGHTING,
    COMPLETION,
    CODE_INSIGHT,
    NAVIGATION,
    FIND_USAGES,
    REFACTORING,
    RENAME_REFACTORING,
    INLINE_REFACTORING,
    MOVE_REFACTORING,
    EXTRACT_REFACTORING,
    EXTRACT_FUNCTION_REFACTORING,
    EXTRACT_VARIABLE_REFACTORING,
    INSPECTIONS,
    INTENTIONS,
    QUICKFIXES,
    GRADLE,
    SCRIPTS,
    DEBUGGER,
    J2K
}

fun MutableTWorkspace.testGroup(
    modulePath: String,
    testSourcesPath: String = "test",
    testDataPath: String = "testData",
    category: GroupCategory = GroupCategory.UNCATEGORIZED,
    block: MutableTGroup.() -> Unit
) {
    groups += TGroupImpl(
        modulePath,
        testSourcesPath,
        testDataPath,
        category,
        isCompilerTestData = testDataPath.startsWith(TestKotlinArtifacts.compilerTestDataDir.canonicalPath)
    ).apply(block)
}