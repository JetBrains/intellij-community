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

package com.jetbrains.packagesearch.intellij.plugin.maven

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.FileWatcherSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.FlowModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.util.awaitSmart
import com.jetbrains.packagesearch.intellij.plugin.util.dumbService
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.messageBusFlow
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProject
import java.nio.file.Paths

internal class MavenSyncSignalProvider : FlowModuleChangesSignalProvider {

    override fun registerModuleChangesListener(project: Project) =
        project.messageBusFlow(MavenImportListener.TOPIC) {
            project.dumbService.awaitSmart()
            object : MavenImportListener {
                override fun importFinished(importedProjects: MutableCollection<MavenProject>, newModules: MutableList<Module>) {
                    logDebug("MavenModuleChangesSignalProvider#registerModuleChangesListener#ProjectDataImportListener")
                    trySend(Unit)
                }
            }
        }
}

internal class GlobalMavenSettingsChangedSignalProvider
    : FileWatcherSignalProvider(Paths.get(System.getProperty("user.home"), ".m2", "settings.xml"))
