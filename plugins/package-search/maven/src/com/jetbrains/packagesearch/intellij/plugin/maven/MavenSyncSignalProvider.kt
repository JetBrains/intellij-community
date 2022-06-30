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

import com.intellij.openapi.project.Project
import com.intellij.util.messages.SimpleMessageBusConnection
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DumbAwareMessageBusModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.FileWatcherSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.invoke
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import org.jetbrains.idea.maven.project.MavenImportListener
import java.nio.file.Paths

internal class MavenSyncSignalProvider : DumbAwareMessageBusModuleChangesSignalProvider() {

    override fun registerDumbAwareModuleChangesListener(project: Project, bus: SimpleMessageBusConnection, listener: Runnable) {
        bus.subscribe(
            MavenImportListener.TOPIC,
            MavenImportListener { _, _ ->
                logDebug("MavenModuleChangesSignalProvider#registerModuleChangesListener#ProjectDataImportListener")
                listener()
            }
        )
    }
}

internal class GlobalMavenSettingsChangedSignalProvider
    : FileWatcherSignalProvider(Paths.get(System.getProperty("user.home"), ".m2", "settings.xml"))
