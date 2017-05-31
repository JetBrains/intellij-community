package circlet.features.livevcs

import circlet.*
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
import runtime.async.*
import runtime.reactive.*

private val log = KLoggers.logger("app-idea/LiveVcsFileWatcher.kt")

class LiveVcsFileWatcher(private val project: Project,
                         private val changeListManager: ChangeListManager,
                         private val fileDocumentManager: FileDocumentManager,
                         private val connection: CircletConnectionComponent,
                         private val vcsManager : ProjectLevelVcsManager,
                         editorFactory: EditorFactory)
    : ILifetimedComponent by LifetimedComponent(project), DocumentListenerAdapter, VirtualFileListenerAdapter {

    companion object {
        private val DELAY_MS = 1000
    }

    // maybe we should disaptch on background thread?
    private val messageQueue = MergingUpdateQueue("LiveVcsFileWatcher::messageQueue",
        DELAY_MS, true, MergingUpdateQueue.ANY_COMPONENT, project)

    private val client: CircletClient? get() = if (connection.client.hasValue) connection.client.value else null

    init {
        connection.client.view(componentLifetime, { lt, client ->
            client ?: return@view

            editorFactory.eventMulticaster.addDocumentListener(this)
            lt.add { editorFactory.eventMulticaster.removeDocumentListener(this) }

            // todo here bug!! contents are nulls
            for (change in changeListManager.allChanges) {
                processChange(change)
            }
        })
    }

    override fun documentChanged(e: DocumentEvent) {
        val document = e.document
        val vFile = fileDocumentManager.getFile(document) ?: return
        queueMessageFor(vFile)
    }

    private fun queueMessageFor(vFile: VirtualFile) {
        if (!vFile.isInLocalFileSystem) return

        messageQueue.queue(object : Update("SendMessage") {
            override fun run() {
                val change = changeListManager.getChange(vFile) ?: return
                processChange(change)
            }

            override fun isDisposed(): Boolean = project.isDisposed
        })
    }

    override fun contentsChanged(event: VirtualFileEvent) {
        queueMessageFor(event.file)
    }

    override fun fileDeleted(event: VirtualFileEvent) {
        queueMessageFor(event.file)
    }

    override fun fileCreated(event: VirtualFileEvent) {
        queueMessageFor(event.file)
    }

    override fun fileMoved(event: VirtualFileMoveEvent) {
        queueMessageFor(event.file)
    }

    private fun processChange(change: Change) {
        val client = client ?: return
        log.info { change.toString() }
        val mutation = client.api.ql.mutation
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

