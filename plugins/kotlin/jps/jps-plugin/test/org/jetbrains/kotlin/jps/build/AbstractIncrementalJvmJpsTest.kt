// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.build

import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.jps.model.k2JvmCompilerArguments

abstract class AbstractIncrementalJvmJpsTest(
    allowNoFilesWithSuffixInTestData: Boolean = false
) : AbstractIncrementalJpsTest(allowNoFilesWithSuffixInTestData = allowNoFilesWithSuffixInTestData) {
    override fun overrideModuleSettings() {
        myProject.k2JvmCompilerArguments = K2JVMCompilerArguments().also {
            it.disableDefaultScriptingPlugin = true
        }
    }
}