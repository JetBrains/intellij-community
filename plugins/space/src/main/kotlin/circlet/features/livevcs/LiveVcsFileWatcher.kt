package circlet.features.livevcs

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

private val log = KLoggers.logger("app-idea/LiveVcsFileWatcher.kt")

class LiveVcsFileWatcher(private val project: Project,
                         private val changeListManager: ChangeListManager,
                         private val fileDocumentManager: FileDocumentManager,
                         private val editorFactory: EditorFactory)
    : ILifetimedComponent by LifetimedComponent(project), DocumentListenerAdapter, VirtualFileListenerAdapter {
    companion object {
        private val DELAY_MS = 1000
    }

    // maybe we should disaptch on background thread?
    private val messageQueue = MergingUpdateQueue("LiveVcsFileWatcher::messageQueue",
        DELAY_MS, true, MergingUpdateQueue.ANY_COMPONENT, project)

    init {
        editorFactory.eventMulticaster.addDocumentListener(this, project)

        async {
            for (change in changeListManager.allChanges) {
                processChange(change)
            }
        }
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
                async { processChange(change) }
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

    suspend private fun processChange(change: Change) {
        log.info { change.toString() }

        when (change.type) {
            Change.Type.MODIFICATION -> {
                val path = change.path ?: return
                val oldText = change.oldText
                val newText = change.newText
            }
            Change.Type.NEW -> {
                val path = change.path ?: return
                val newText = change.newText
            }
            Change.Type.DELETED -> {
                val path = change.path ?: return
            }
            Change.Type.MOVED -> {
                // todo
            }
            else -> throw IllegalArgumentException("change.type")
        }
    }
}

private val Change.path: String? get() = virtualFile?.canonicalPath
private val Change.oldText: String? get() = beforeRevision?.content
private val Change.newText: String? get() = afterRevision?.content

