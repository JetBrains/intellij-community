// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.configuration.KotlinAutoConfigurationNotificationHolder

@Service(Service.Level.PROJECT)
internal class KotlinMavenAutoConfigurationNotificationHolder(project: Project) :
    KotlinAutoConfigurationNotificationHolder(project) {
    override val buildSystemDocumentationUrl: String =
        KotlinMavenBundle.message("auto.configure.kotlin.documentation.maven.url")
    override val buildSystemName: String = KotlinMavenBundle.message("maven.name")

    companion object {
        fun getInstance(project: Project): KotlinMavenAutoConfigurationNotificationHolder {
            return project.service()
        }
    }
}