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
                         editorFactory: EditorFactory)
    : ILifetimedComponent by LifetimedComponent(project), DocumentListenerAdapter, VirtualFileListenerAdapter {

    companion object {
        private val PUSH_DELAY_MS = 1 * 1000
        private val PULL_DELAY_MS = 1 * 1000
    }

    private val client: CircletClient? get() = if (connection.client.hasValue) connection.client.value else null

    init {
        connection.client.view(componentLifetime, { lt, client ->
            client ?: return@view

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

            val lifetimes = SequentialLifetimes(lt)
            Dispatch.dispatchInterval(PULL_DELAY_MS, lt, {
                pullConflicts(lifetimes.next())
            })
        })
    }

    private fun pullConflicts(lifetime: Lifetime) {
        val client = client ?: return
        log.trace { "Pull conflicts" }

        val query = client.query
        async {
            val modifiedFiles = query.livevcs {
                this.modifiedFiles {
                    this.path()
                    this.changes {
                        this.newText()
                        this.user {
                            this.email()
                            this.firstName()
                            this.lastName()
                            this.avatar()
                        }
                    }
                }
            }.modifiedFiles

            if (!lifetime.isTerminated) {
                processConflicts(lifetime, modifiedFiles)
            }
        }
    }

    private fun processConflicts(lifetime: Lifetime, modifiedFiles: Array<ModifiedFileWire>) {
        for (changesPerFile in modifiedFiles)
            MyTask(lifetime, changesPerFile, this).scheduleTask()
    }

    private class MyTask(private val lifetime: Lifetime,
                         private val changes: ModifiedFileWire,
                         private val owner: LiveVcsFileWatcher) : ReadTask() {

        fun scheduleTask() {
            val pi = ProgressIndicatorBase()
            lifetime.add {
                pi.cancel()
            }
            ProgressIndicatorUtils.scheduleWithWriteActionPriority(pi, this)
        }

        override fun performInReadAction(indicator: ProgressIndicator): Continuation? {
            if (owner.project.isDisposed || lifetime.isTerminated) return null

            val result = doWork(indicator) ?: return null
            return Continuation({
                application.assertIsDispatchThread()
                if (lifetime.isTerminated || indicator.isCanceled) return@Continuation

                val myHighlighters = mutableSetOf<RangeHighlighter>()
                val markupModel: MarkupModel? = if (result.isEmpty()) return@Continuation
                else DocumentMarkupModel.forDocument(result.first().doc, owner.project, false/*???*/)

                for (patch in result) {
                    val (aliveChanges, conflicts, doc) = patch
                    val user = patch.user
                    for (change in aliveChanges) {
                        // insertion
//                    if (change.length == 0) {
//                        // folding
//                    }
//                    else {
                        val highlighter = markupModel.addRangeHighlighter(change.start, change.last,
                            HighlighterLayer.ELEMENT_UNDER_CARET, null, HighlighterTargetArea.EXACT_RANGE)

                        val isDeletion = change.text.isEmpty()
                        val bgColor = if (isDeletion) Color.GRAY else Color.GREEN
                        val statusBarTxt = "Text was modified remotly"
                        val tooltipTxt = if (isDeletion) null else change.text
                        val textAttributes = TextAttributes(null, Color.GRAY, null, EffectType.BOXED, 0)

                        val highlightInfo = object : HighlightInfo(textAttributes, null, HighlightInfoType.INFORMATION,
                            change.start, change.last, statusBarTxt, tooltipTxt, HighlightSeverity.INFORMATION,
                            false, false, false, 0, null, null) {}

                        highlightInfo.highlighter = highlighter as RangeHighlighterEx
                        highlighter.errorStripeTooltip = highlightInfo
//                    }
                    }
                }

                lifetime.add {
                    markupModel ?: run { assert(myHighlighters.isEmpty()); return@add }
                    for (h in myHighlighters) {
                        markupModel.removeHighlighter(h)
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
                if (indicator.isCanceled) return emptyList()
                val path = changes.path
                val patches = changes.changes.map { createPath(path, it) }.filterNotNull()
                return patches
            } catch (pce: ProcessCanceledException) {
                throw pce
            } catch (t: Throwable) {
                log.error(t)
                return emptyList()
            }
        }

        private fun createPath(path: String, c: FileChangeOverviewWire): Patch? {
            val user = c.user
            val remoteText = c.newText

            val vFile = findFileByVcsRoots(path) ?: return null
            if (!vFile.isInLocalFileSystem) return null

            val status = owner.changeListManager.getStatus(vFile)

            if (status != FileStatus.MODIFIED) {
                log.warn { "We got conflicts for added file: ${path}" }
                return null
            }

            // getting already loaded document
            val document = owner.fileDocumentManager.getCachedDocument(vFile) ?: return null

            val change = owner.changeListManager.getChange(vFile)
            val baseText = change?.oldText ?: document.text/* if file's not modified */
            val myText = change?.newText ?: document.text/* if file's not modified */
            val remoteChanges = diff(baseText, remoteText)
            val localChanges = diff(baseText, myText)

            val (aliveChanges, conflicts) = intersects(localChanges, remoteChanges)
            if (aliveChanges.isEmpty() && conflicts.isEmpty()) return null

            return Patch(aliveChanges, conflicts, document, user)
        }

        override fun onCanceled(indicator: ProgressIndicator) {
            if (lifetime.isTerminated) return
            MyTask(lifetime, changes, owner).scheduleTask()
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
        log.trace { "Push change from $origin: $change" }
        val mutation = client.mutation
        async {
            when (change.type) {
                Change.Type.MODIFICATION -> {
                    val path = change.file ?: return@async
                    val oldText = change.oldText ?: return@async
                    val newText = change.newText ?: return@async
                    mutation.addFileChanged(path.getRelativePath(), oldText, newText)
                }
                Change.Type.NEW -> {
                    val path = change.file ?: return@async
                    val newText = change.newText ?: return@async
                    mutation.addFileCreated(path.getRelativePath(), newText)
                }
                Change.Type.DELETED -> {
                    val path = change.file ?: return@async
                    mutation.addFileRemoved(path.getRelativePath())
                }
                Change.Type.MOVED -> {
                    val oldPath = change.oldFile ?: return@async
                    val newPath = change.file ?: return@async
                    val newText = change.newText ?: return@async
                    mutation.addFileRemoved(oldPath.getRelativePath())
                    mutation.addFileCreated(newPath.getRelativePath(), newText)
                }
                else -> {
                    log.error { "undefined type: ${change.type.name}" }
                }
            }
        }
    }

    private fun VirtualFile.getRelativePath(): String {
        val root = vcsManager.getVcsRootFor(this)!!
        return FileUtilRt.getRelativePath(VfsUtil.virtualToIoFile(root), VfsUtil.virtualToIoFile(this)) ?: run {
            log.error { "Can't make relative path from: base = ${root.path} , path = ${this.path}" }
            throw IllegalArgumentException()
        }
    }
}

private val Change.oldFile: VirtualFile? get() = beforeRevision?.file?.virtualFile
private val Change.file: VirtualFile? get() = virtualFile
private val Change.oldText: String? get() = beforeRevision?.content
private val Change.newText: String? get() = afterRevision?.content

