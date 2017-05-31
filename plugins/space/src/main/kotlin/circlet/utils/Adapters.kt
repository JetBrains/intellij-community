package circlet.utils

import com.intellij.openapi.editor.event.*
import com.intellij.openapi.vfs.*


interface DocumentListenerAdapter : DocumentListener {
    override fun documentChanged(e: DocumentEvent) = Unit

    override fun beforeDocumentChange(e: DocumentEvent) = Unit
}

interface VirtualFileListenerAdapter : VirtualFileListener {
    override fun beforePropertyChange(event: VirtualFilePropertyEvent) {

    }

    override fun beforeContentsChange(event: VirtualFileEvent) {

    }

    override fun fileDeleted(event: VirtualFileEvent) {

    }

    override fun beforeFileMovement(event: VirtualFileMoveEvent) {

    }

    override fun fileMoved(event: VirtualFileMoveEvent) {

    }

    override fun propertyChanged(event: VirtualFilePropertyEvent) {

    }

    override fun contentsChanged(event: VirtualFileEvent) {

    }

    override fun beforeFileDeletion(event: VirtualFileEvent) {

    }

    override fun fileCreated(event: VirtualFileEvent) {

    }

    override fun fileCopied(event: VirtualFileCopyEvent) {

    }
}
