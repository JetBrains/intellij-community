// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.build

import org.jetbrains.kotlin.incremental.testingUtils.Modification
import org.jetbrains.kotlin.test.KotlinRoot

class IncrementalRenameModuleTest : AbstractIncrementalJpsTest() {
    fun testRenameModule() {
        doTest(KotlinRoot.DIR.path + "/jps/jps-plugin/tests/testData/incremental/custom/renameModule/")
    }

    override fun performAdditionalModifications(modifications: List<Modification>) {
        projectDescriptor.project.modules.forEach { it.name += "Renamed" }
    }
}