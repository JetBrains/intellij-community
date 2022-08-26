// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.wizard.service

import com.intellij.ide.fileTemplates.FileTemplateUtil
import org.jetbrains.kotlin.tools.projectWizard.core.service.TemplateEngineService
import org.jetbrains.kotlin.tools.projectWizard.templates.FileTemplateDescriptor

class IdeaVelocityEngineTemplateService : TemplateEngineService(), IdeaWizardService {
    override fun renderTemplate(template: FileTemplateDescriptor, data: Map<String, Any?>): String {
        val templateText = getTemplateText(template)
        return FileTemplateUtil.mergeTemplate(data, templateText, false)
    }
}