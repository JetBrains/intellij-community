// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.base.fir.projectStructure.AbstractKMPProjectStructureTest
import org.jetbrains.kotlin.idea.base.fir.projectStructure.AbstractKaModuleStructureTest
import org.jetbrains.kotlin.testGenerator.model.GroupCategory
import org.jetbrains.kotlin.testGenerator.model.MutableTWorkspace
import org.jetbrains.kotlin.testGenerator.model.Patterns.DIRECTORY
import org.jetbrains.kotlin.testGenerator.model.model
import org.jetbrains.kotlin.testGenerator.model.testClass
import org.jetbrains.kotlin.testGenerator.model.testGroup

fun MutableTWorkspace.generateProjectStructureTest() {
    testGroup("base/fir/project-structure", category = GroupCategory.PROJECT_STRUCTURE) {
        testClass<AbstractKaModuleStructureTest> {
            model("kaModuleStructure", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractKMPProjectStructureTest> {
            model("kmp", pattern = DIRECTORY, isRecursive = false)
        }
    }
}