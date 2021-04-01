package com.jetbrains.packagesearch.intellij.plugin.extensions.maven

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.connect
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IVoidSignal
import com.jetbrains.rd.util.reactive.Signal
import com.jetbrains.rd.util.reactive.fire
import org.jetbrains.idea.maven.project.MavenImportListener

internal class MavenModuleChangesSignalProvider : ModuleChangesSignalProvider {

    override fun listenToModuleChanges(project: Project, lifetime: Lifetime): IVoidSignal {
        val signal = Signal.Void()
        project.messageBus.connect(lifetime)
            .subscribe(
                MavenImportListener.TOPIC,
                MavenImportListener { _, _ -> signal.fire() }
            )
        return signal
    }
}
