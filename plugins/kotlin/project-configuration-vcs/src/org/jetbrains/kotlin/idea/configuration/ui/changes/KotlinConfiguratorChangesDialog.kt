// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration.ui.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.TextRevisionNumber
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.ui.OnePixelSplitter
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.kotlin.idea.configuration.KotlinConfiguratorChangedFile
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import javax.swing.JComponent

class KotlinConfiguratorChangesDialog(
    private val project: Project,
    changedFiles: List<KotlinConfiguratorChangedFile>
) : FrameWrapper(project) {
    private val changes = changedFiles.mapNotNull(::createChange)

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

    private fun createRevision(contents: String, filePath: FilePath, name: String): ContentRevision {
        return object : ContentRevision {
            override fun getContent(): String = contents

            override fun getFile(): FilePath = filePath

            override fun getRevisionNumber(): VcsRevisionNumber = TextRevisionNumber(name, name)
        }
    }

    private fun createChange(changedFile: KotlinConfiguratorChangedFile): Change? {
        val virtualFile = changedFile.file.virtualFile ?: return null
        val filePath = VcsUtil.getFilePath(virtualFile)
        val originalRevision = createRevision(
            changedFile.originalContent,
            filePath,
            KotlinProjectConfigurationBundle.message("configure.kotlin.original.content")
        )
        val newRevision = createRevision(
            changedFile.modifiedContent,
            filePath,
            KotlinProjectConfigurationBundle.message("configure.kotlin.modified.content")
        )
        return Change(originalRevision, newRevision, FileStatus.MODIFIED)
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