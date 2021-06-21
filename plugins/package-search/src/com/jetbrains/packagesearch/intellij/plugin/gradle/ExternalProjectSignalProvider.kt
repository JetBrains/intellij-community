package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.util.messages.SimpleMessageBusConnection
import com.jetbrains.packagesearch.intellij.plugin.extensibility.invoke
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DumbAwareMessageBusModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener
import org.jetbrains.plugins.gradle.settings.TestRunner
import java.util.function.Supplier

internal class ExternalProjectSignalProvider : DumbAwareMessageBusModuleChangesSignalProvider() {

    override fun registerDumbAwareModuleChangesListener(project: Project, bus: SimpleMessageBusConnection, listener: Supplier<Unit>) {
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

    override fun registerDumbAwareModuleChangesListener(project: Project, bus: SimpleMessageBusConnection, listener: Supplier<Unit>) =
        listener()
}

internal class GradleModuleLinkSignalProvider : DumbAwareMessageBusModuleChangesSignalProvider() {

    override fun registerDumbAwareModuleChangesListener(project: Project, bus: SimpleMessageBusConnection, listener: Supplier<Unit>) {
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
