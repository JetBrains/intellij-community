// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.wizard.service

import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.framework.ui.ConfigureDialogWithModulesAndVersion
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.core.asNullable
import org.jetbrains.kotlin.tools.projectWizard.core.safe
import org.jetbrains.kotlin.tools.projectWizard.core.service.EapVersionDownloader
import org.jetbrains.kotlin.tools.projectWizard.core.service.KotlinVersionProviderService
import org.jetbrains.kotlin.tools.projectWizard.core.service.WizardKotlinVersion
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.settings.version.Version
import org.jetbrains.kotlin.tools.projectWizard.wizard.KotlinNewProjectWizardUIBundle
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.runWithProgressBar

@NonNls
private const val SNAPSHOT_TAG = "snapshot"

class IdeaKotlinVersionProviderService : KotlinVersionProviderService(), IdeaWizardService {
    override fun getKotlinVersion(projectKind: ProjectKind): WizardKotlinVersion {
        val version = if (projectKind == ProjectKind.KMM) {
            Versions.KOTLIN_FOR_COMPOSE
        } else {
            getPatchedKotlinVersion() ?: getKotlinVersionFromCompiler() ?: Versions.KOTLIN
        }

        return kotlinVersionWithDefaultValues(version)
    }

    /**
     * This allows QA to change the version used in the wizard by supplying the
     * KOTLIN_COMPILER_VERSION_TAG property.
     */
    private fun getPatchedKotlinVersion() =
        if (isApplicationInternalMode()) {
            System.getProperty(KOTLIN_COMPILER_VERSION_TAG)?.let { Version.fromString(it) }
        } else {
            null
        }

    companion object {
        private const val KOTLIN_COMPILER_VERSION_TAG = "kotlin.compiler.version"

        /**
         * Returns the Kotlin version used by the bundled compiler in case it is not
         * a snapshot or release artifact.
         *
         * In IJ release cycle (IntelliJ `master` branch and IntelliJ release branches),
         * `standaloneCompilerVersion` is always release, so this function will return null
         * in those cases
         */
        private fun getKotlinVersionFromCompiler(): Version? {
            val kotlinCompilerVersion = KotlinPluginLayout.standaloneCompilerVersion
            val kotlinArtifactVersion = kotlinCompilerVersion
                .takeUnless { it.isSnapshot || it.isRelease }
                ?.withoutBuildNumber()
                ?.artifactVersion ?: return null
            return Version.fromString(kotlinArtifactVersion)
        }
    }
}

private object VersionsDownloader {
    fun downloadLatestEapOrStableKotlinVersion(): Version? =
        runWithProgressBar(KotlinNewProjectWizardUIBundle.message("version.provider.downloading.kotlin.version")) {
            val latestEap = EapVersionDownloader.getLatestEapVersion()
            val latestStable = getLatestStableVersion()
            when {
                latestEap == null -> latestStable
                latestStable == null -> latestEap
                VersionComparatorUtil.compare(latestEap.text, latestStable.text) > 0 -> latestEap
                else -> latestStable
            }
        }

    private fun getLatestStableVersion() = safe {
        ConfigureDialogWithModulesAndVersion.loadVersions("1.0.0")
    }.asNullable?.firstOrNull { !it.contains(SNAPSHOT_TAG, ignoreCase = true) }?.let { Version.fromString(it) }
}
