// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.openapi.module.ModuleType
import org.jetbrains.kotlin.idea.KotlinIcons
import javax.swing.Icon

class NewProjectWizardModuleType : ModuleType<NewProjectWizardModuleBuilder>(NewProjectWizardModuleBuilder.MODULE_BUILDER_ID) {
    override fun getName(): String = KotlinNewProjectWizardUIBundle.message("generator.title")
    override fun getDescription(): String = name
    override fun getNodeIcon(isOpened: Boolean): Icon = KotlinIcons.SMALL_LOGO
    override fun createModuleBuilder(): NewProjectWizardModuleBuilder = NewProjectWizardModuleBuilder()
}