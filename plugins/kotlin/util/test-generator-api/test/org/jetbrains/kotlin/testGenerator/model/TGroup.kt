// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.testGenerator.model

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.UNCATEGORIZED
import java.io.File

interface TGroup {

    val pluginMode: KotlinPluginMode

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
    J2K,
    ANALYSIS_API,
    INJECTION,
    PROJECT_STRUCTURE,
}

fun MutableTWorkspace.testGroup(
    modulePath: String,
    testSourcesPath: String = "test",
    testDataPath: String = "testData",
    category: GroupCategory = UNCATEGORIZED,
    block: MutableTGroup.() -> Unit,
) {
    groups += object : MutableTGroup {

        override val pluginMode: KotlinPluginMode
            get() = this@testGroup.pluginMode

        override val modulePath: String
            get() = modulePath

        override val testSourcesPath: String
            get() = testSourcesPath

        override val testDataPath: String
            get() = testDataPath

        override val category: GroupCategory
            get() = category

        override val isCompilerTestData: Boolean =
            testDataPath.startsWith(TestKotlinArtifacts.compilerTestDataDir.canonicalPath)

        override val kotlinRoot: File
            get() = KotlinRoot.DIR

        override val moduleRoot: File =
            File(kotlinRoot, modulePath)

        override val testSourcesRoot: File =
            File(moduleRoot, testSourcesPath)

        override val testDataRoot: File =
            File(testDataPath).takeIf { it.isAbsolute }
                ?: File(moduleRoot, testDataPath)

        override val suites: MutableList<TSuite> =
            mutableListOf()
    }.apply(block)
}