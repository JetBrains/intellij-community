package com.jetbrains.packagesearch.intellij.plugin.extensions.gradle

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.connect
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IVoidSignal
import com.jetbrains.rd.util.reactive.Signal
import com.jetbrains.rd.util.reactive.fire

internal class GradleModuleChangesSignalProvider : ModuleChangesSignalProvider {

    override fun listenToModuleChanges(project: Project, lifetime: Lifetime): IVoidSignal {
        val signal = Signal.Void()
        project.messageBus.connect(lifetime)
            .subscribe(
                ProjectDataImportListener.TOPIC,
                ProjectDataImportListener { signal.fire() }
            )
        return signal
    }
}
