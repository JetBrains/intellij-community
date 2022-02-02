// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.versions

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.ui.EditorNotificationProvider.*
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NotNull
import org.jetbrains.kotlin.idea.*
import org.jetbrains.kotlin.idea.project.TargetPlatformDetector
import org.jetbrains.kotlin.idea.util.application.invokeLater
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.metadata.deserialization.BinaryVersion
import org.jetbrains.kotlin.platform.js.isJs
import org.jetbrains.kotlin.platform.jvm.isJvm
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.function.Function
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.event.HyperlinkEvent

class UnsupportedAbiVersionNotificationPanelProvider : EditorNotificationProvider {

    private fun doCreate(fileEditor: FileEditor, project: Project, module: Module, badVersionedRoots: Collection<BinaryVersionedFile<BinaryVersion>>): EditorNotificationPanel {
        val answer = ErrorNotificationPanel(fileEditor)
        val badRootFiles = badVersionedRoots.map { it.file }

        val kotlinLibraries = findAllUsedLibraries(project).keySet()
        val badRuntimeLibraries = kotlinLibraries.filter { library ->
            val runtimeJar = LibraryJarDescriptor.STDLIB_JAR.findExistingJar(library)?.let { VfsUtil.getLocalFile(it) }
            val jsLibJar = LibraryJarDescriptor.JS_STDLIB_JAR.findExistingJar(library)?.let { VfsUtil.getLocalFile(it) }
            badRootFiles.contains(runtimeJar) || badRootFiles.contains(jsLibJar)
        }

        val isPluginOldForAllRoots = badVersionedRoots.all { it.supportedVersion < it.version }
        val isPluginNewForAllRoots = badVersionedRoots.all { it.supportedVersion > it.version }

        when {
            badRuntimeLibraries.isNotEmpty() -> {
                val badRootsInRuntimeLibraries = findBadRootsInRuntimeLibraries(badRuntimeLibraries, badVersionedRoots)
                val otherBadRootsCount = badVersionedRoots.size - badRootsInRuntimeLibraries.size

                val text = KotlinJvmBundle.htmlMessage(
                    "html.b.0.choice.0.1.1.some.kotlin.runtime.librar.0.choice.0.1.y.1.ies.b.1.choice.0.1.and.one.other.jar.1.and.1.other.jars.1.choice.0.has.0.have.an.unsupported.binary.format.html",
                    badRuntimeLibraries.size,
                    otherBadRootsCount
                )

                answer.text = text

                if (isPluginOldForAllRoots) {
                    createUpdatePluginLink(answer)
                }

                val isPluginOldForAllRuntimeLibraries = badRootsInRuntimeLibraries.all { it.supportedVersion < it.version }
                val isPluginNewForAllRuntimeLibraries = badRootsInRuntimeLibraries.all { it.supportedVersion > it.version }

                val updateAction = when {
                    isPluginNewForAllRuntimeLibraries -> KotlinJvmBundle.message("button.text.update.library")
                    isPluginOldForAllRuntimeLibraries -> KotlinJvmBundle.message("button.text.downgrade.library")
                    else -> KotlinJvmBundle.message("button.text.replace.library")
                }

                val actionLabelText = "$updateAction " + KotlinJvmBundle.message(
                    "0.choice.0.1.1.all.kotlin.runtime.librar.0.choice.0.1.y.1.ies",
                    badRuntimeLibraries.size
                )

                answer.createActionLabel(actionLabelText) {
                    ApplicationManager.getApplication().invokeLater {
                        updateLibraries(project, kotlinCompilerVersionShort(), badRuntimeLibraries)
                    }
                }
            }

            badVersionedRoots.size == 1 -> {
                val badVersionedRoot = badVersionedRoots.first()
                val presentableName = badVersionedRoot.file.presentableName

                when {
                    isPluginOldForAllRoots -> {
                        answer.text = KotlinJvmBundle.htmlMessage(
                            "html.kotlin.library.b.0.b.was.compiled.with.a.newer.kotlin.compiler.and.can.t.be.read.please.update.kotlin.plugin.html",
                            presentableName
                        )
                        createUpdatePluginLink(answer)
                    }

                    isPluginNewForAllRoots ->
                        answer.text = KotlinJvmBundle.htmlMessage(
                            "html.kotlin.library.b.0.b.has.outdated.binary.format.and.can.t.be.read.by.current.plugin.please.update.the.library.html",
                            presentableName
                        )

                    else -> {
                        throw IllegalStateException("Bad root with compatible version found: $badVersionedRoot")
                    }
                }

                answer.createActionLabel(KotlinJvmBundle.message("button.text.go.to.0", presentableName)) {
                    navigateToLibraryRoot(
                        project,
                        badVersionedRoot.file
                    )
                }
            }

            isPluginOldForAllRoots -> {
                answer.text =
                    KotlinJvmBundle.message("some.kotlin.libraries.attached.to.this.project.were.compiled.with.a.newer.kotlin.compiler.and.can.t.be.read.please.update.kotlin.plugin")
                createUpdatePluginLink(answer)
            }

            isPluginNewForAllRoots ->
                answer.setText(
                    KotlinJvmBundle.message("some.kotlin.libraries.attached.to.this.project.have.outdated.binary.format.and.can.t.be.read.by.current.plugin.please.update.found.libraries")
                )

            else ->
                answer.setText(KotlinJvmBundle.message("some.kotlin.libraries.attached.to.this.project.have.unsupported.binary.format.please.update.the.libraries.or.the.plugin"))

        }

        createShowPathsActionLabel(project, module, answer, KotlinJvmBundle.message("button.text.details"))

        return answer
    }

    private fun createShowPathsActionLabel(project: Project, module: Module, answer: EditorNotificationPanel, @NlsContexts.LinkLabel labelText: String) {
        answer.createComponentActionLabel(labelText) { label ->
            val task = {
                val badRoots = collectBadRoots(module)
                assert(!badRoots.isEmpty()) { "This action should only be called when bad roots are present" }

                val listPopupModel = LibraryRootsPopupModel(
                    KotlinJvmBundle.message("unsupported.format.plugin.version.0", KotlinPluginUtil.getPluginVersion()),
                    project,
                    badRoots
                )
                val popup = JBPopupFactory.getInstance().createListPopup(listPopupModel)
                popup.showUnderneathOf(label)

                null
            }
            DumbService.getInstance(project).tryRunReadActionInSmartMode(
                task,
                KotlinJvmBundle.message("can.t.show.all.paths.during.index.update")
            )
        }
    }

    private fun createUpdatePluginLink(answer: ErrorNotificationPanel) {
        answer.createProgressAction(
            KotlinJvmBundle.message("progress.action.text.check"),
            KotlinJvmBundle.message("progress.action.text.update.plugin")
        ) { link, updateLink ->
            KotlinPluginUpdater.getInstance().runCachedUpdate { pluginUpdateStatus ->
                when (pluginUpdateStatus) {
                    is PluginUpdateStatus.Update -> {
                        link.isVisible = false
                        updateLink.isVisible = true

                        updateLink.addHyperlinkListener(object : HyperlinkAdapter() {
                            override fun hyperlinkActivated(e: HyperlinkEvent) {
                                KotlinPluginUpdater.getInstance().installPluginUpdate(pluginUpdateStatus)
                            }
                        })
                    }
                    is PluginUpdateStatus.LatestVersionInstalled -> {
                        link.text = KotlinJvmBundle.message("no.updates.found")
                    }
                }

                false  // do not auto-retry update check
            }
        }
    }

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?> {
        if (file.extension != KotlinFileType.EXTENSION && !FileTypeRegistry.getInstance().isFileOfType(file, KotlinFileType.INSTANCE)) {
            return CONST_NULL
        }
        try {
            if (DumbService.isDumb(project) || isUnitTestMode()) return CONST_NULL

            if (CompilerManager.getInstance(project).isExcludedFromCompilation(file)) return CONST_NULL

            val module = ModuleUtilCore.findModuleForFile(file, project) ?: return CONST_NULL

            return Function { checkAndCreate(it, project, module) }
        } catch (e: ProcessCanceledException) {
            // Ignore
        } catch (e: IndexNotReadyException) {
            DumbService.getInstance(project).runWhenSmart { updateNotifications(project) }
        }

        return CONST_NULL
    }

    fun checkAndCreate(fileEditor: FileEditor, project: Project, module: Module): EditorNotificationPanel? {
        val state = project.service<SuppressNotificationState>().state
        if (state.isSuppressed) {
            return null
        }

        val badRoots = collectBadRoots(module)
        if (!badRoots.isEmpty()) {
            return doCreate(fileEditor, project, module, badRoots)
        }

        return null
    }

    private fun findBadRootsInRuntimeLibraries(
        badRuntimeLibraries: List<Library>,
        badVersionedRoots: Collection<BinaryVersionedFile<BinaryVersion>>
    ): ArrayList<BinaryVersionedFile<BinaryVersion>> {
        val badRootsInLibraries = ArrayList<BinaryVersionedFile<BinaryVersion>>()

        fun addToBadRoots(file: VirtualFile?) {
            if (file != null) {
                val runtimeJarBadRoot = badVersionedRoots.firstOrNull { it.file == file }
                if (runtimeJarBadRoot != null) {
                    badRootsInLibraries.add(runtimeJarBadRoot)
                }
            }
        }

        badRuntimeLibraries.forEach { library ->
            for (descriptor in LibraryJarDescriptor.values()) {
                addToBadRoots(descriptor.findExistingJar(library)?.let<VirtualFile, @NotNull VirtualFile> { VfsUtil.getLocalFile(it) })
            }
        }

        return badRootsInLibraries
    }

    private class LibraryRootsPopupModel(
        @NlsContexts.PopupTitle title: String,
        private val project: Project,
        roots: Collection<BinaryVersionedFile<BinaryVersion>>
    ) : BaseListPopupStep<BinaryVersionedFile<BinaryVersion>>(title, *roots.toTypedArray()) {

        override fun getTextFor(root: BinaryVersionedFile<BinaryVersion>): String {
            val relativePath = VfsUtilCore.getRelativePath(root.file, project.baseDir, '/')
            return KotlinJvmBundle.message("0.1.expected.2", relativePath ?: root.file.path, root.version, root.supportedVersion)
        }

        override fun getIconFor(aValue: BinaryVersionedFile<BinaryVersion>): Icon = if (aValue.file.isDirectory) {
            AllIcons.Nodes.Folder
        } else {
            AllIcons.FileTypes.Archive
        }

        override fun onChosen(selectedValue: BinaryVersionedFile<BinaryVersion>, finalChoice: Boolean): PopupStep<*>? {
            navigateToLibraryRoot(project, selectedValue.file)
            return PopupStep.FINAL_CHOICE
        }

        override fun isSpeedSearchEnabled(): Boolean = true
    }

    private class ErrorNotificationPanel(fileEditor: FileEditor) : EditorNotificationPanel(fileEditor) {
        init {
            myLabel.icon = AllIcons.General.Error
        }

        fun createProgressAction(@Nls text: String, @Nls successLinkText: String, updater: (JLabel, HyperlinkLabel) -> Unit) {
            val label = JLabel(text)
            myLinksPanel.add(label)

            val successLink = createActionLabel(successLinkText) { }
            successLink.isVisible = false

            // Several notification panels can be created almost instantly but we want to postpone deferred checks until
            // panels are actually visible on screen.
            myLinksPanel.addComponentListener(object : ComponentAdapter() {
                var isUpdaterCalled = false
                override fun componentResized(p0: ComponentEvent?) {
                    if (!isUpdaterCalled) {
                        isUpdaterCalled = true
                        updater(label, successLink)
                    }
                }
            })
        }
    }

    private fun updateNotifications(project: Project) {
        invokeLater {
            if (!project.isDisposed) {
                EditorNotifications.getInstance(project).updateAllNotifications()
            }
        }
    }

    companion object {
        private fun navigateToLibraryRoot(project: Project, root: VirtualFile) {
            OpenFileDescriptor(project, root).navigate(true)
        }

        fun collectBadRoots(module: Module): Collection<BinaryVersionedFile<BinaryVersion>> {
            val platform = TargetPlatformDetector.getPlatform(module)
            val badRoots = when {
                platform.isJvm() -> getLibraryRootsWithAbiIncompatibleKotlinClasses(module)
                platform.isJs() -> getLibraryRootsWithAbiIncompatibleForKotlinJs(module)
                // TODO: also check it for Native KT-34525
                else -> return emptyList()
            }

            return if (badRoots.isEmpty()) emptyList() else badRoots.toHashSet()
        }
    }
}

fun EditorNotificationPanel.createComponentActionLabel(@NlsContexts.LinkLabel labelText: String, callback: (HyperlinkLabel) -> Unit) {
    val label: Ref<HyperlinkLabel> = Ref.create()
    val action = Runnable {
        callback(label.get())
    }
    label.set(createActionLabel(labelText, action))
}

private operator fun BinaryVersion.compareTo(other: BinaryVersion): Int {
    val first = this.toArray()
    val second = other.toArray()
    for (i in 0 until maxOf(first.size, second.size)) {
        val thisPart = first.getOrNull(i) ?: -1
        val otherPart = second.getOrNull(i) ?: -1

        if (thisPart != otherPart) {
            return thisPart - otherPart
        }
    }

    return 0
}
