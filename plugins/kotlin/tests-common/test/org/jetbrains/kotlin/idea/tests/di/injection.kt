// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.tests.di

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.cfg.ControlFlowInformationProviderImpl
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.getValue
import org.jetbrains.kotlin.container.useImpl
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.context.ModuleContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.frontend.di.configureModule
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValueFactory
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices

fun createContainerForTests(project: Project, module: ModuleDescriptor): ContainerForTests {
    return ContainerForTests(createContainer("Tests", JvmPlatformAnalyzerServices) {
        configureModule(
            ModuleContext(module, project, "container for tests"),
            JvmPlatforms.defaultJvmPlatform,
            JvmPlatformAnalyzerServices,
            BindingTraceContext(),
            LanguageVersionSettingsImpl.DEFAULT
        )
        useImpl<AnnotationResolverImpl>()
        useInstance(ModuleStructureOracle.SingleModule)
        useInstance(ControlFlowInformationProviderImpl.Factory)
    })
}

class ContainerForTests(container: StorageComponentContainer) {
    val expressionTypingServices: ExpressionTypingServices by container
    val dataFlowValueFactory: DataFlowValueFactory by container
}
