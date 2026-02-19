// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.core.service

import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.TargetJvmVersion
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind

abstract class JvmTargetVersionsProviderService: WizardService {

    abstract fun listSupportedJvmTargetVersions(projectKind: ProjectKind): Set<TargetJvmVersion>
}