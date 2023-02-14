// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.phases

enum class GenerationPhase {
    PREPARE, INIT_TEMPLATE, FIRST_STEP, SECOND_STEP, PREPARE_GENERATION, PROJECT_GENERATION, PROJECT_IMPORT

    ;

    companion object {
        val ALL = values().toSet()

        fun startingFrom(firstPhase: GenerationPhase) =
            values().filter { it >= firstPhase }.toSet()
    }
}

