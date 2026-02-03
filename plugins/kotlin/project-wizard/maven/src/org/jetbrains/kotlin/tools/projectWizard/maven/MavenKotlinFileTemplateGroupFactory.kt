// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.maven

import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory
import icons.OpenapiIcons

class MavenKotlinFileTemplateGroupFactory: FileTemplateGroupDescriptorFactory {

    override fun getFileTemplatesDescriptor(): FileTemplateGroupDescriptor {
        val group = FileTemplateGroupDescriptor("Maven", OpenapiIcons.RepositoryLibraryLogo) //NON-NLS
        group.addTemplate(FileTemplateDescriptor(MAVEN_KOTLIN_PROJECT_XML_TEMPLATE, OpenapiIcons.RepositoryLibraryLogo))
        return group
    }

    companion object {
        const val MAVEN_KOTLIN_PROJECT_XML_TEMPLATE = "Maven Kotlin Project.xml"
    }
}
