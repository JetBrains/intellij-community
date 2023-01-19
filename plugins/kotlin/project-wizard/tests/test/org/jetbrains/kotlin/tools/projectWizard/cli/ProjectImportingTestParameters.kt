// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.cli

import java.nio.file.Path

data class DefaultTestParameters(
    val runForMaven: Boolean = false,
    val runForGradleGroovy: Boolean = true,
    val keepKotlinVersion: Boolean = false
) : TestParameters {
    companion object {
        fun fromTestDataOrDefault(directory: Path): DefaultTestParameters =
            TestParameters.fromTestDataOrDefault(directory, PARAMETERS_FILE_NAME)

        private const val PARAMETERS_FILE_NAME = "importParameters.txt"
    }
}