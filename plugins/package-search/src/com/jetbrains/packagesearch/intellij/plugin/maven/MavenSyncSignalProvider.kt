package com.jetbrains.packagesearch.intellij.plugin.maven

import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import com.intellij.util.messages.SimpleMessageBusConnection
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DumbAwareMessageBusModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.FlowModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.invoke
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.timer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.idea.maven.project.MavenImportListener
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.getLastModifiedTime
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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

internal class GlobalMavenSettingsChangedSignalProvider : FlowModuleChangesSignalProvider {

    override fun registerModuleChangesListener(project: Project): Flow<Unit> {

        val path: Path = Paths.get(System.getProperty("user.home"), ".m2", "settings.xml")

        val getTime = { if (path.exists()) path.getLastModifiedTime().toMillis() else 0 }

        return timer(1.toDuration(DurationUnit.SECONDS))
            .map { getTime() }
            .stateIn(project.lifecycleScope, SharingStarted.Lazily, getTime())
            .drop(1)
            .map { }
    }
}
