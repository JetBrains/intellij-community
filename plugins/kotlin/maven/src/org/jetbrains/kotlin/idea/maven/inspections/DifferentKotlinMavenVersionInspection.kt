// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven.inspections

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.util.xml.DomFileElement
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomElementsInspection
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.inspections.PluginVersionDependentInspection
import org.jetbrains.kotlin.idea.maven.KotlinMavenBundle
import org.jetbrains.kotlin.idea.maven.PomFile

class DifferentKotlinMavenVersionInspection : DomElementsInspection<MavenDomProjectModel>(MavenDomProjectModel::class.java),
    PluginVersionDependentInspection {
    private val idePluginVersion by lazy { KotlinPluginLayout.ideCompilerVersion.languageVersion }

    override var testVersionMessage: String? = null
        @TestOnly set

    override fun checkFileElement(domFileElement: DomFileElement<MavenDomProjectModel>, holder: DomElementAnnotationHolder) {
        val project = domFileElement.module?.project ?: return
        val mavenManager = MavenProjectsManager.getInstance(project) ?: return

        if (!mavenManager.isMavenizedProject || !mavenManager.isManagedFile(domFileElement.file.virtualFile)) {
            return
        }

        val pomFile = PomFile.forFileOrNull(domFileElement.file) ?: return
        for (plugin in pomFile.findKotlinPlugins()) {
            if (!plugin.version.exists()) continue
            val mavenPluginVersion = IdeKotlinVersion.parse(plugin.version.stringValue ?: continue)
            val mavenPluginLanguageVersion = mavenPluginVersion.getOrNull()?.languageVersion ?: continue

            if (idePluginVersion < mavenPluginLanguageVersion || mavenPluginLanguageVersion < LanguageVersion.FIRST_SUPPORTED) {
                createProblem(holder, plugin)
            }

        }
    }

    private fun createProblem(holder: DomElementAnnotationHolder, plugin: MavenDomPlugin) {
        val versionFromMaven = plugin.version.stringValue
        val versionFromIde = testVersionMessage ?: idePluginVersion

        holder.createProblem(
            plugin.version,
            HighlightSeverity.WARNING,
            KotlinMavenBundle.message("version.different.maven.ide", versionFromMaven.toString(), versionFromIde)
        )
    }
}