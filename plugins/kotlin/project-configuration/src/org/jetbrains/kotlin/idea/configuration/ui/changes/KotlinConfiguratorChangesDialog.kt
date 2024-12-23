// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration.ui.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.ui.OnePixelSplitter
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import javax.swing.JComponent

class KotlinConfiguratorChangesDialog(
    private val project: Project,
    private val changes: List<Change>
) : FrameWrapper(project) {
    private val changesToProducers = changes.mapNotNull {
        val producer = ChangeDiffRequestProducer.create(project, it) ?: return@mapNotNull null
        it to producer
    }.toMap()

    private val changesTree =  KotlinConfiguratorChangesBrowser(project, changes, ::showChange)
    private val diffProcessor = KotlinConfiguratorChangesDiffRequestProcessor(project) {
        close()
    }

    init {
        closeOnEsc()
        title = KotlinProjectConfigurationBundle.message("configure.kotlin.changes.title")

        component = createCenterPanel()
        show()
    }


    private fun showChange(selected: Change?) {
        diffProcessor.setProvider(changesToProducers[selected])
    }

    private fun createCenterPanel(): JComponent {
        val panel = OnePixelSplitter(0.3f)

        panel.firstComponent = changesTree
        panel.secondComponent = diffProcessor.component
        return panel
    }

    override fun dispose() {
        super.dispose()
        changesTree.shutdown()
        Disposer.dispose(diffProcessor)
    }
}