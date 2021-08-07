// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.compilerRunner

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.preloading.ClassCondition
import org.jetbrains.kotlin.utils.KotlinPaths

class JpsCompilerEnvironment(
    services: Services,
    val classesToLoadByParent: ClassCondition,
    messageCollector: MessageCollector,
    outputItemsCollector: OutputItemsCollectorImpl,
    val progressReporter: ProgressReporter
) : CompilerEnvironment(services, messageCollector, outputItemsCollector) {
    override val outputItemsCollector: OutputItemsCollectorImpl
        get() = super.outputItemsCollector as OutputItemsCollectorImpl
}
