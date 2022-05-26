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
