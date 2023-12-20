// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.update

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.kotlin.idea.KotlinPluginUpdater
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.util.application.isHeadlessEnvironment
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.isKotlinFileType

internal class KotlinPluginUpdateCheckerStartActivity : ProjectActivity {
    init {
        if (isUnitTestMode() || isHeadlessEnvironment()) {
            throw ExtensionNotApplicableException.create()
        }
    }

    override suspend fun execute(project: Project) {
        val documentListener: DocumentListener = object : DocumentListener {
            override fun documentChanged(e: DocumentEvent) {
                FileDocumentManager.getInstance().getFile(e.document)?.let { virtualFile ->
                    if (virtualFile.isKotlinFileType() && virtualFile.isInLocalFileSystem) {
                        KotlinPluginUpdater.getInstance().pluginUsed()
                    }
                }
            }
        }

        EditorFactory.getInstance().eventMulticaster
            .addDocumentListener(documentListener, KotlinPluginDisposable.getInstance(project))
    }
}