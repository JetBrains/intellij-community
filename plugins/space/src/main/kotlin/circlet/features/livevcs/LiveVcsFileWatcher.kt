package circlet.features.livevcs

import circlet.*
import circlet.components.*
import circlet.utils.*
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.*
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
                    val path = change.path ?: return@async
                    val oldText = change.oldText ?: return@async
                    val newText = change.newText ?: return@async
                    mutation.addFileChanged(path, oldText, newText)
                }
                Change.Type.NEW -> {
                    val path = change.path ?: return@async
                    val newText = change.newText ?: return@async
                    mutation.addFileCreated(path, newText)
                }
                Change.Type.DELETED -> {
                    val path = change.path ?: return@async
                    mutation.addFileRemoved(path)
                }
                Change.Type.MOVED -> {
                    val oldPath = change.oldPath ?: return@async
                    val newPath = change.path ?: return@async
                    val newText = change.newText ?: return@async
                    mutation.addFileRemoved(oldPath)
                    mutation.addFileCreated(newPath, newText)
                }
                else -> {
                    log.error { "undefined type: ${change.type.name}" }
                }
            }
        }
    }
}

private val Change.oldPath: String? get() = beforeRevision?.file?.virtualFile?.canonicalPath
private val Change.path: String? get() = virtualFile?.canonicalPath
private val Change.oldText: String? get() = beforeRevision?.content
private val Change.newText: String? get() = afterRevision?.content

