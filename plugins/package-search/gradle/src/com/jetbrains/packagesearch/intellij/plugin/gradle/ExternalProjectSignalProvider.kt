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

package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.FileWatcherSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.FlowModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.util.awaitSmart
import com.jetbrains.packagesearch.intellij.plugin.util.dumbService
import com.jetbrains.packagesearch.intellij.plugin.util.filesChangedEventFlow
import com.jetbrains.packagesearch.intellij.plugin.util.messageBusFlow
import com.jetbrains.packagesearch.intellij.plugin.util.trySend
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener
import org.jetbrains.plugins.gradle.settings.TestRunner
import java.nio.file.Paths

internal class SmartModeSignalProvider : FlowModuleChangesSignalProvider {

    override fun registerModuleChangesListener(project: Project) = flow {
        project.dumbService.awaitSmart()
        emit(Unit)
    }
}

internal class GradleModuleLinkSignalProvider : FlowModuleChangesSignalProvider {

    override fun registerModuleChangesListener(project: Project) =
        project.messageBusFlow(GradleSettingsListener.TOPIC) {
            project.dumbService.awaitSmart()
            object : GradleSettingsListener {
                override fun onProjectRenamed(oldName: String, newName: String) {
                    trySend()
                }

                override fun onProjectsLinked(settings: MutableCollection<GradleProjectSettings>) {
                    trySend()
                }

                override fun onProjectsUnlinked(linkedProjectPaths: MutableSet<String>) {
                    trySend()
                }

                override fun onGradleHomeChange(oldPath: String?, newPath: String?, linkedProjectPath: String) {
                    trySend()
                }

                override fun onGradleDistributionTypeChange(currentValue: DistributionType?, linkedProjectPath: String) {
                    trySend()
                }

                override fun onServiceDirectoryPathChange(oldPath: String?, newPath: String?) {
                    trySend()
                }

                override fun onGradleVmOptionsChange(oldOptions: String?, newOptions: String?) {
                    trySend()
                }

                override fun onBuildDelegationChange(delegatedBuild: Boolean, linkedProjectPath: String) {
                    trySend()
                }

                override fun onTestRunnerChange(currentTestRunner: TestRunner, linkedProjectPath: String) {
                    trySend()
                }
            }
        }
}

internal class GradlePropertiesChangedSignalProvider : FlowModuleChangesSignalProvider {

    override fun registerModuleChangesListener(project: Project) =
        project.filesChangedEventFlow.flatMapMerge { it.asFlow() }
            .filter { it.file?.name == "gradle.properties" || it.file?.name == "local.properties" }
            .map { }
}

internal class GlobalGradlePropertiesChangedSignalProvider : FileWatcherSignalProvider(
    System.getenv("GRADLE_USER_HOME")?.let { Paths.get(it, "gradle.properties") }
        ?: Paths.get(System.getProperty("user.home"), ".gradle", "gradle.properties")
)
