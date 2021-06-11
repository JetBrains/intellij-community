package com.jetbrains.packagesearch.intellij.plugin.extensions.maven

import com.intellij.util.messages.SimpleMessageBusConnection
import com.jetbrains.packagesearch.intellij.plugin.extensibility.invoke
import com.jetbrains.packagesearch.intellij.plugin.extensions.AbstractMessageBusModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import org.jetbrains.idea.maven.project.MavenImportListener
import java.util.function.Supplier

internal class MavenSyncSignalProvider : AbstractMessageBusModuleChangesSignalProvider() {

    override fun registerModuleChangesListener(bus: SimpleMessageBusConnection, listener: Supplier<Unit>) {
        bus.subscribe(
            MavenImportListener.TOPIC,
            MavenImportListener { _, _ ->
                logDebug("MavenModuleChangesSignalProvider#registerModuleChangesListener#ProjectDataImportListener")
                listener()
            }
        )
    }
}
