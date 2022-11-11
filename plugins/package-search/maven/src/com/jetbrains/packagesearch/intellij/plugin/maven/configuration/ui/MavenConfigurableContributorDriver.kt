/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.maven.configuration.ui

import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.project.Project
import com.intellij.ui.RelativeFont
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.FormBuilder
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.AnalyticsAwareConfigurableContributorDriver
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.maven.configuration.PackageSearchMavenConfiguration
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.util.addOnTextChangedListener
import javax.swing.JLabel

internal class MavenConfigurableContributorDriver(project: Project) : AnalyticsAwareConfigurableContributorDriver {

    private var isMavenDefaultScopeChanged: Boolean = false

    private val configuration = PackageSearchMavenConfiguration.getInstance(project)

    private val mavenScopeEditor = PackageSearchUI.textField {
        addOnTextChangedListener {
            isMavenDefaultScopeChanged = text != configuration.defaultMavenScope
        }
    }

    override fun contributeUserInterface(builder: FormBuilder) {
        builder.addComponent(
            TitledSeparator(PackageSearchBundle.message("packagesearch.configuration.maven.title")),
            0
        )
        builder.addLabeledComponent(
            PackageSearchBundle.message("packagesearch.configuration.maven.scopes.default"),
            mavenScopeEditor
        )

        val label = JLabel(
            " ${PackageSearchBundle.message("packagesearch.configuration.maven.scopes")} " +
                configuration.getMavenScopes().joinToString(", ")
        )
        builder.addComponentToRightColumn(
            RelativeFont.TINY.install(RelativeFont.ITALIC.install(label))
        )
    }

    override fun isModified() = isMavenDefaultScopeChanged

    override fun reset() {
        mavenScopeEditor.text = configuration.determineDefaultMavenScope()
        isMavenDefaultScopeChanged = false
    }

    override fun restoreDefaults() {
        val defaultMavenScope = configuration.determineDefaultMavenScope()
        isMavenDefaultScopeChanged = mavenScopeEditor.text != defaultMavenScope
        mavenScopeEditor.text = defaultMavenScope
    }

    override fun apply() {
        configuration.defaultMavenScope = mavenScopeEditor.text
        isMavenDefaultScopeChanged = false
    }

    override fun provideApplyEventAnalyticsData(): List<EventPair<*>> = listOf(
        PackageSearchEventsLogger.preferencesDefaultMavenScopeChangedField
            .with(configuration.defaultMavenScope != PackageSearchMavenConfiguration.DEFAULT_MAVEN_SCOPE)
    )
}
