package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.util.messages.SimpleMessageBusConnection
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DumbAwareMessageBusModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.FlowModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.invoke
import com.jetbrains.packagesearch.intellij.plugin.util.filesChangedEventFlow
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.timer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener
import org.jetbrains.plugins.gradle.settings.TestRunner
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal class ExternalProjectSignalProvider : DumbAwareMessageBusModuleChangesSignalProvider() {

    override fun registerDumbAwareModuleChangesListener(project: Project, bus: SimpleMessageBusConnection, listener: Runnable) {
        bus.subscribe(
            ProjectDataImportListener.TOPIC,
            ProjectDataImportListener {
                logDebug("ExternalProjectSignalProvider#registerModuleChangesListener#ProjectDataImportListener") { "value=$it" }
                listener()
            }
        )
    }
}

internal class SmartModeSignalProvider : DumbAwareMessageBusModuleChangesSignalProvider() {

    override fun registerDumbAwareModuleChangesListener(project: Project, bus: SimpleMessageBusConnection, listener: Runnable) =
        listener()
}

internal class GradleModuleLinkSignalProvider : DumbAwareMessageBusModuleChangesSignalProvider() {

    override fun registerDumbAwareModuleChangesListener(project: Project, bus: SimpleMessageBusConnection, listener: Runnable) {
        bus.subscribe(
            GradleSettingsListener.TOPIC,
            object : GradleSettingsListener {
                override fun onProjectRenamed(oldName: String, newName: String) = listener()

                override fun onProjectsLinked(settings: MutableCollection<GradleProjectSettings>) = listener()

                override fun onProjectsUnlinked(linkedProjectPaths: MutableSet<String>) = listener()

                override fun onBulkChangeStart() {}

                override fun onBulkChangeEnd() {}

                override fun onGradleHomeChange(oldPath: String?, newPath: String?, linkedProjectPath: String) {}

                override fun onGradleDistributionTypeChange(currentValue: DistributionType?, linkedProjectPath: String) {}

                override fun onServiceDirectoryPathChange(oldPath: String?, newPath: String?) {}

                override fun onGradleVmOptionsChange(oldOptions: String?, newOptions: String?) {}

                override fun onBuildDelegationChange(delegatedBuild: Boolean, linkedProjectPath: String) {}

                override fun onTestRunnerChange(currentTestRunner: TestRunner, linkedProjectPath: String) {}
            }
        )
    }
}

internal class GradlePropertiesChangedSignalProvider : FlowModuleChangesSignalProvider {

    override fun registerModuleChangesListener(project: Project) =
        project.filesChangedEventFlow.flatMapLatest { it.asFlow() }
            .filter { it.file?.name == "gradle.properties" }
            .map { }
}

internal class LocalPropertiesChangedSignalProvider : FlowModuleChangesSignalProvider {

    override fun registerModuleChangesListener(project: Project) =
        project.filesChangedEventFlow.flatMapLatest { it.asFlow() }
            .filter { it.file?.name == "local.properties" }
            .map { }
}

internal class GlobalGradlePropertiesChangedSignalProvider : FlowModuleChangesSignalProvider {

    override fun registerModuleChangesListener(project: Project): Flow<Unit> {
        val path: Path = System.getenv("GRADLE_USER_HOME")?.let { Paths.get(it, "gradle.properties") }
            ?: Paths.get(System.getProperty("user.home"), ".gradle", "gradle.properties")

        val getTime = { if (path.exists()) path.getLastModifiedTime().toMillis() else 0 }

        return timer(1.toDuration(DurationUnit.SECONDS))
            .map { getTime() }
            .stateIn(project.lifecycleScope, SharingStarted.Lazily, getTime())
            .drop(1)
            .map { }
    }
}
