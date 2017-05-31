package circlet.features.livevcs

import circlet.*
import circlet.api.client.graphql.*
import circlet.components.*
import circlet.utils.*
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.*
import com.intellij.openapi.util.io.*
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vfs.*
import com.intellij.util.ui.update.*
import klogging.*
import runtime.*
import runtime.async.*
import runtime.reactive.*

private val log = KLoggers.logger("app-idea/LiveVcsFileWatcher.kt")

class LiveVcsFileWatcher(private val project: Project,
                         private val changeListManager: ChangeListManager,
                         private val fileDocumentManager: FileDocumentManager,
                         private val connection: CircletConnectionComponent,
                         private val vcsManager: ProjectLevelVcsManager,
                         editorFactory: EditorFactory)
    : ILifetimedComponent by LifetimedComponent(project), DocumentListenerAdapter, VirtualFileListenerAdapter {

    companion object {
        private val PUSH_DELAY_MS = 1 * 1000
        private val PULL_DELAY_MS = 10 * 1000
    }

    // todo maybe we should disaptch on background thread?
    private val messageQueue = MergingUpdateQueue("LiveVcsFileWatcher::messageQueue",
        PUSH_DELAY_MS, true, MergingUpdateQueue.ANY_COMPONENT, project)

    private val client: CircletClient? get() = if (connection.client.hasValue) connection.client.value else null

    init {
        connection.client.view(componentLifetime, { lt, client ->
            client ?: return@view

            editorFactory.eventMulticaster.addDocumentListener(this)
            lt.add { editorFactory.eventMulticaster.removeDocumentListener(this) }

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
            val conflictingFiles = query.livevcs {
                this.conflictingFiles {
                    this.path()
                    this.newText()
                    this.user {
                        this.id()
                        this.firstName()
                        this.lastName()
                    }
                }
            }.conflictingFiles

            if (!lifetime.isTerminated) {
                processConflicts(lifetime, conflictingFiles)
            }
        }
    }

    private fun processConflicts(lifetime: Lifetime, conflictingFiles: Array<ConflictingFileWire>) {
        application.invokeLater {
            for (conflict in conflictingFiles) {
                // todo
            }
        }

        lifetime.add {
            application.invokeLater {
                // todo
            }
        }
    }

    override fun documentChanged(e: DocumentEvent) {
        val document = e.document
        val vFile = fileDocumentManager.getFile(document) ?: return
        queueMessageFor(vFile, "doc_change")
    }

    private fun queueMessageFor(vFile: VirtualFile, origin: String) {
        if (!vFile.isInLocalFileSystem) return

        messageQueue.queue(object : Update("SendMessage") {
            override fun run() {
                val change = changeListManager.getChange(vFile) ?: return
                pushChange(change, origin)
            }

            override fun isDisposed(): Boolean = project.isDisposed
        })
    }

    override fun contentsChanged(event: VirtualFileEvent) {
        queueMessageFor(event.file, "vfs_content_change")
    }

    override fun fileDeleted(event: VirtualFileEvent) {
        queueMessageFor(event.file, "vfs_file_delte")
    }

    override fun fileCreated(event: VirtualFileEvent) {
        queueMessageFor(event.file, "vfs_file_created")
    }

    override fun fileMoved(event: VirtualFileMoveEvent) {
        queueMessageFor(event.file, "vfs_file_moved")
    }

    private fun pushChange(change: Change, origin: String) {
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

