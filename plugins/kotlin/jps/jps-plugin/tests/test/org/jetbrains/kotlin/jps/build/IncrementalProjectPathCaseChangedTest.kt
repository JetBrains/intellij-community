// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.build

import com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.kotlin.incremental.testingUtils.Modification
import org.jetbrains.kotlin.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.util.slashedPath

class IncrementalProjectPathCaseChangedTest : AbstractIncrementalJpsTest(checkDumpsCaseInsensitively = true) {
    fun testProjectPathCaseChanged() {
        doTest("jps/jps-plugin/tests/testData/incremental/custom/projectPathCaseChanged")
    }

    fun testProjectPathCaseChangedMultiFile() {
        doTest("jps/jps-plugin/tests/testData/incremental/custom/projectPathCaseChangedMultiFile")
    }

    override fun doTest(testDataPath: String) {
        if (SystemInfoRt.isFileSystemCaseSensitive) {
            return
        }

        super.doTest(KotlinRoot.DIR.resolve(testDataPath).slashedPath)
    }

    override fun performAdditionalModifications(modifications: List<Modification>) {
        val module = myProject.modules[0]
        val sourceRoot = module.sourceRoots[0].url
        assert(sourceRoot.endsWith("/src"))
        val newSourceRoot = sourceRoot.replace("/src", "/SRC")
        module.removeSourceRoot(sourceRoot, JavaSourceRootType.SOURCE)
        module.addSourceRoot(newSourceRoot, JavaSourceRootType.SOURCE)
    }
}
