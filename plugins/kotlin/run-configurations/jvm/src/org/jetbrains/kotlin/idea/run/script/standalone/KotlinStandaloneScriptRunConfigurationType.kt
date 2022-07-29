// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.run.script.standalone

import com.intellij.execution.application.JvmMainMethodRunConfigurationOptions
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.KotlinRunConfigurationsBundle

class KotlinStandaloneScriptRunConfigurationType : SimpleConfigurationType(
    "KotlinStandaloneScriptRunConfigurationType",
    KotlinRunConfigurationsBundle.message("name.kotlin.script"),
    KotlinRunConfigurationsBundle.message("run.kotlin.script"),
    NotNullLazyValue.createValue { KotlinIcons.SMALL_LOGO }
) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return KotlinStandaloneScriptRunConfiguration(project, this, "")
    }

    override fun getOptionsClass() = JvmMainMethodRunConfigurationOptions::class.java

    override fun isEditableInDumbMode(): Boolean = true

    override fun isDumbAware(): Boolean = true

    companion object {
        val instance: KotlinStandaloneScriptRunConfigurationType
            get() = ConfigurationTypeUtil.findConfigurationType(KotlinStandaloneScriptRunConfigurationType::class.java)
    }
}
