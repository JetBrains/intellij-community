// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.ui.EditorNotifications
import org.jetbrains.kotlin.idea.util.isJavaFileType

class KotlinUpdateNotificationListener(private val project: Project) : ModuleRootListener, DumbService.DumbModeListener, BulkFileListener {
    override fun after(events: MutableList<out VFileEvent>) {
        for (event in events) {
            if (event is VFileMoveEvent && event.file.isJavaFileType()) {
                editorNotifications.updateNotifications(event.file)
            }
        }
    }

    override fun exitDumbMode(): Unit = editorNotifications.updateAllNotifications()
    override fun rootsChanged(event: ModuleRootEvent): Unit = editorNotifications.updateAllNotifications()

    private val editorNotifications: EditorNotifications get() = EditorNotifications.getInstance(project)
}
