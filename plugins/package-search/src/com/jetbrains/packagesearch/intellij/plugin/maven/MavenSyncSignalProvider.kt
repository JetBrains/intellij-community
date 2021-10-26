package com.jetbrains.packagesearch.intellij.plugin.maven

import com.intellij.openapi.project.Project
import com.intellij.util.messages.SimpleMessageBusConnection
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DumbAwareMessageBusModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.invoke
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import org.jetbrains.idea.maven.project.MavenImportListener

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
