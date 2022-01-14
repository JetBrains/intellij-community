package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.util.messages.SimpleMessageBusConnection
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DumbAwareMessageBusModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.FileWatcherSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.FlowModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.invoke
import com.jetbrains.packagesearch.intellij.plugin.util.filesChangedEventFlow
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener
import org.jetbrains.plugins.gradle.settings.TestRunner
import java.nio.file.Paths

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
        project.filesChangedEventFlow.flatMapMerge { it.asFlow() }
            .filter { it.file?.name == "gradle.properties" || it.file?.name == "local.properties" }
            .map { }
}

internal class GlobalGradlePropertiesChangedSignalProvider : FileWatcherSignalProvider(
    System.getenv("GRADLE_USER_HOME")?.let { Paths.get(it, "gradle.properties") }
        ?: Paths.get(System.getProperty("user.home"), ".gradle", "gradle.properties")
)
