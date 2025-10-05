// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.runConfigurations.jvm.script

import com.intellij.execution.application.JvmMainMethodRunConfigurationOptions
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.KotlinRunConfigurationsBundle
import org.jetbrains.kotlin.idea.runConfigurations.jvm.script.KotlinStandaloneScriptRunConfiguration

fun kotlinStandaloneScriptRunConfigurationType(): KotlinStandaloneScriptRunConfigurationType {
    return ConfigurationTypeUtil.findConfigurationType(KotlinStandaloneScriptRunConfigurationType::class.java)
}

class KotlinStandaloneScriptRunConfigurationType : SimpleConfigurationType(
    "KotlinStandaloneScriptRunConfigurationType",
    KotlinRunConfigurationsBundle.message("name.kotlin.script"),
    KotlinRunConfigurationsBundle.message("run.kotlin.script"),
    NotNullLazyValue.createValue { KotlinIcons.SCRIPT }
) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        KotlinStandaloneScriptRunConfiguration(project, this, "")

    override fun getOptionsClass() = JvmMainMethodRunConfigurationOptions::class.java

    override fun isEditableInDumbMode(): Boolean = true

    override fun isDumbAware(): Boolean = true
}
