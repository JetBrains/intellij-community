package circlet.features.livevcs

import circlet.*
import circlet.api.client.graphql.*
import circlet.components.*
import circlet.utils.*
import com.intellij.codeInsight.daemon.impl.*
import com.intellij.lang.annotation.*
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.*
import com.intellij.openapi.editor.impl.*
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.*
import com.intellij.openapi.project.*
import com.intellij.openapi.util.io.*
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vfs.*
import com.intellij.util.*
import klogging.*
import runtime.*
import runtime.TextChange
import runtime.async.*
import runtime.reactive.*
import java.awt.*

private val log = KLoggers.logger("app-idea/LiveVcsFileWatcher.kt")

class LiveVcsFileWatcher(private val project: Project,
                         private val changeListManager: ChangeListManager,
                         private val fileDocumentManager: FileDocumentManager,
                         private val connection: CircletConnectionComponent,
                         private val vcsManager: ProjectLevelVcsManager,
                         vFileManager: VirtualFileManager,
                         editorFactory: EditorFactory,
                         val fileEditorManager: FileEditorManager)
    : ILifetimedComponent by LifetimedComponent(project), DocumentListenerAdapter, VirtualFileListenerAdapter {

    companion object {
        private val PUSH_DELAY_MS = 1 * 1000
    }

    private val client: CircletClient? get() = if (connection.client.hasValue) connection.client.value else null

    val haacklt = SequentialLifetimes(componentLifetime)

    init {
        val seq = SequentialLifetimes(componentLifetime)

        connection.client.view(componentLifetime, { lt, client ->
            client ?: return@view
            val nextLt = seq.next()
            Alarm().addRequest({
                if (!nextLt.isTerminated) {

                    editorFactory.eventMulticaster.addDocumentListener(this)
                    vFileManager.addVirtualFileListener(this)
                    lt.add {
                        vFileManager.removeVirtualFileListener(this)
                        editorFactory.eventMulticaster.removeDocumentListener(this)
                    }

                    // todo here bug!! contents are nulls
                    for (change in changeListManager.allChanges) {
                        pushChange(change, "initial")
                    }
                    val poller = SequentialLifetimes(nextLt)
                    pullConflicts(poller, client)

                }
            }, 2000)
        })
    }

    private fun pullConflicts(poller: SequentialLifetimes, client: CircletClient) {
        log.info { "pullConflicts" }
        async {
            val lifetime = poller.next()
            try {
                val modifiedFiles = client.query.livevcs {
                    this.modifiedFiles {
                        this.path()
                        this.changes {
                            this.newText()
                            this.user {
                                this.id()
                                this.email()
                                this.firstName()
                                this.lastName()
                                this.avatar()
                            }
                        }
                    }
                }.modifiedFiles
                log.info { "got mofified files" }
                if (!lifetime.isTerminated) {
                    processConflicts(lifetime, modifiedFiles, client)
                    log.info { "done processing conflicts" }
                    AlarmFactory.getInstance().create().addRequest({
                        pullConflicts(poller, client)
                    }, 5000)
                }
            } catch (th: Throwable) {
                poller.next()
                Dispatch.dispatchInterval(2000, {
                    pullConflicts(poller, client)
                })
                log.error(th)
            }
        }
    }

    private suspend fun processConflicts(lifetime: Lifetime, modifiedFiles: Array<ModifiedFileWire>, client: CircletClient) {
        MyTask(lifetime, modifiedFiles, this, client).scheduleTask()
    }

    private class MyTask(private val lifetime: Lifetime,
                         private val modifiedFiles: Array<ModifiedFileWire>,
                         private val owner: LiveVcsFileWatcher, val client: CircletClient) : ReadTask() {

        override fun onCanceled(indicator: ProgressIndicator) {
        }

        suspend fun scheduleTask() {
            val pi = ProgressIndicatorBase()
            lifetime.add {
                pi.cancel()
            }
            asyncCallback<Unit> { next ->
                ProgressIndicatorUtils.scheduleWithWriteActionPriority(pi, this).whenComplete({ result, error ->
                    if (error != null)
                        next.resumeWithException(error)
                    else
                        next.resume(Unit)
                })
            }
        }

        override fun performInReadAction(indicator: ProgressIndicator): Continuation? {
            log.info { "start read action" }
            if (owner.project.isDisposed || lifetime.isTerminated) return null

            val result = doWork(indicator) ?: return null

            return Continuation({
                log.info { "start read action continuation" }

                val lt = owner.haacklt.next()

                application.assertIsDispatchThread()
                if (lifetime.isTerminated || indicator.isCanceled) return@Continuation

                for (patch in result) {
                    for (change in patch.aliveChanges) {

                        val markupModel = DocumentMarkupModel.forDocument(patch.doc, owner.project, true)

                        val highlighter = markupModel.addRangeHighlighter(change.start, change.last,
                            3700, TextAttributes(null, Color(208, 208, 208), null, EffectType.BOXED, 0), HighlighterTargetArea.EXACT_RANGE)

                        val isDeletion = change.text.isEmpty()
                        val bgColor = if (isDeletion) Color.GRAY else Color.GREEN
                        val statusBarTxt = "Text was modified remotly"
                        val tooltipTxt = if (isDeletion) null else change.text
                        val textAttributes = TextAttributes(null, bgColor, null, EffectType.BOXED, 0)

                        val highlightInfo = object : HighlightInfo(textAttributes, null, HighlightInfoType.INFORMATION,
                            change.start, change.last, statusBarTxt, tooltipTxt, HighlightSeverity.INFORMATION,
                            false, false, false, 0, null, null) {}

                        highlightInfo.highlighter = highlighter as RangeHighlighterEx
                        highlighter.errorStripeTooltip = highlightInfo

                        log.info { "put highlighter at [${highlightInfo.startOffset}, ${highlightInfo.endOffset}] in length=${patch.doc.textLength}" }

                        lt.add { markupModel.removeHighlighter(highlighter) }
                    }
                }

            })
        }

        private data class Patch(val aliveChanges: List<TextChange>,
                                 val conflicts: List<Conflict>,
                                 val doc: Document,
                                 val user: UserWire)

        private fun doWork(indicator: ProgressIndicator): List<Patch> {
            try {
                val patches = mutableListOf<Patch>()
                owner.fileEditorManager.allEditors.toList().filterIsInstance<TextEditor>().forEach {
                    val file = owner.fileDocumentManager.getFile(it.editor.document)
                    if (file != null) {
                        val relativePath = file.getRelativePath(owner.vcsManager)
                        val modifiedFile = modifiedFiles.firstOrNull { it.path == relativePath }
                        if (modifiedFile != null) {
                            for (change in modifiedFile.changes) {
                                if (change.user.id.raw == client.user.uid) continue

                                val patch = createPath(file, change) ?: continue
                                patches.add(patch)
                                indicator.checkCanceled()
                            }
                        }
                    }
                }
                return patches
            } catch (pce: ProcessCanceledException) {
                throw pce
            } catch (t: Throwable) {
                log.error(t)
                return emptyList()
            }
        }

        private fun createPath(vFile: VirtualFile, c: FileChangeOverviewWire): Patch? {
            val remoteText = c.newText
            // getting already loaded document
            val document = owner.fileDocumentManager.getCachedDocument(vFile) ?: return null

            val change = owner.changeListManager.getChange(vFile)
            val baseText = change?.oldText ?: document.text/* if file's not modified */
            val myText = change?.newText ?: document.text/* if file's not modified */
            val remoteChanges = diff(baseText, remoteText)
            val localChanges = diff(baseText, myText)

            val (aliveChanges, conflicts) = intersects(localChanges, remoteChanges)

            log.info { "Alive in ${vFile.presentableName}: ${aliveChanges.map { "${it.range} -> ${it.text.length}" }.joinToString(", ")}" }
            log.info { "Conflicted in ${vFile.presentableName}: ${conflicts.map { "${it.local} -> ${it.remote}" }.joinToString(", ")}" }

            if (aliveChanges.isEmpty() && conflicts.isEmpty()) return null

            return Patch(aliveChanges, conflicts, document, c.user)
        }

        private fun findFileByVcsRoots(path: String): VirtualFile? {
            var vFile: VirtualFile? = null
            for (root in owner.vcsManager.allVcsRoots) {
                val base = root.path
                vFile = VfsUtil.findRelativeFile(base, *path.replace('\\', '/').split("/".toRegex()).toTypedArray())
                if (vFile != null)
                    break
            }
            return vFile
        }
    }

    override fun documentChanged(e: DocumentEvent) {
        val document = e.document
        val vFile = fileDocumentManager.getFile(document) ?: return
        pushChange(vFile, "doc_change")
    }

    override fun contentsChanged(event: VirtualFileEvent) {
        pushChange(event.file, "vfs_content_change")
    }

    override fun fileDeleted(event: VirtualFileEvent) {
        pushChange(event.file, "vfs_file_delte")
    }

    override fun fileCreated(event: VirtualFileEvent) {
        pushChange(event.file, "vfs_file_created")
    }

    override fun fileMoved(event: VirtualFileMoveEvent) {
        pushChange(event.file, "vfs_file_moved")
    }

    private fun pushChange(vFile: VirtualFile, origin: String) {
        if (!vFile.isInLocalFileSystem) return
        val change = changeListManager.getChange(vFile) ?: return
        pushChange(change, origin)
    }

    private fun pushChange(change: Change, origin: String) {
        if (project.isDisposed) return
        val client = client ?: return
        log.trace { "Push change from" }
        val mutation = client.mutation
        async {
            when (change.type) {
                Change.Type.MODIFICATION -> {
                    val path = change.file ?: return@async
                    val oldText = change.oldText ?: return@async
                    val newText = change.newText ?: return@async
                    mutation.addFileChanged(path.getRelativePath(vcsManager), oldText, newText)
                }
                Change.Type.NEW -> {
                    val path = change.file ?: return@async
                    val newText = change.newText ?: return@async
                    mutation.addFileCreated(path.getRelativePath(vcsManager), newText)
                }
                Change.Type.DELETED -> {
                    val path = change.file ?: return@async
                    mutation.addFileRemoved(path.getRelativePath(vcsManager))
                }
                Change.Type.MOVED -> {
                    val oldPath = change.oldFile ?: return@async
                    val newPath = change.file ?: return@async
                    val newText = change.newText ?: return@async
                    mutation.addFileRemoved(oldPath.getRelativePath(vcsManager))
                    mutation.addFileCreated(newPath.getRelativePath(vcsManager), newText)
                }
                else -> {
                    log.error { "undefined type: ${change.type.name}" }
                }
            }
        }
    }

}

fun VirtualFile.getRelativePath(vcsManager: ProjectLevelVcsManager): String {
    val root = vcsManager.getVcsRootFor(this)!!
    return FileUtilRt.getRelativePath(VfsUtil.virtualToIoFile(root), VfsUtil.virtualToIoFile(this)) ?: run {
        log.error { "Can't make relative path from: base = ${root.path} , path = ${this.path}" }
        throw IllegalArgumentException()
    }
}


private val Change.oldFile: VirtualFile? get() = beforeRevision?.file?.virtualFile
private val Change.file: VirtualFile? get() = virtualFile
private val Change.oldText: String? get() = beforeRevision?.content
private val Change.newText: String? get() = afterRevision?.content

