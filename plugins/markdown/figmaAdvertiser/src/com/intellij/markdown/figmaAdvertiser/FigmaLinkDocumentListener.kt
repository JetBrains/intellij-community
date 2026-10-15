package com.intellij.markdown.figmaAdvertiser

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectLocator
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.ui.EditorNotifications
import org.jetbrains.annotations.ApiStatus

/**
 * Asks the platform to recompute the banner over a Markdown file whose text has just gained a link
 * to a Figma design.
 *
 * `EditorNotificationsImpl` recomputes when a file is opened, when dumb mode starts or ends, when
 * the roots change and when a plugin is loaded or unloaded
 * (`EditorNotificationsImpl.kt:97-132`). Editing a file is none of those, so without this the offer
 * over a file that is already open waits for the file to be opened again.
 *
 * Every document in the IDE reaches [documentChanged], on both sides of split mode. So the listener
 * reads no property of the file until the change has met a Figma link. A file of a split frontend
 * can answer its name and path only from the backend. That read can wait for seconds under the
 * write action that delivers the event. The scan reads a window around the change, not the file.
 * A whole-document reset makes that window the whole text.
 */
// `SplitModeApiUsage` asks for a frontend module, and this one is shared. What the listener does with
// the document is read the text around a change, and what it then asks for is a recompute of one
// file's notifications, which does nothing wherever no editor shows that file.
@Suppress("SplitModeApiUsage")
@ApiStatus.Internal
class FigmaLinkDocumentListener : DocumentListener {

  override fun documentChanged(event: DocumentEvent) {
    if (!FigmaAdvertiserRegistry.isSuggestionAllowed) return
    // A frontend process never shows the banner: its projects drop the platform's advertiser
    // notification, which is the only caller of `FigmaConnectPluginSuggestionProvider`. In split
    // mode, the backend runs this listener over its own copy of the same document.
    if (IdeProductMode.isFrontend) return
    // `FileDocumentManagerBase` answers from the document's own user data and reads no property of the file.
    val file = FileDocumentManager.getInstance().getFile(event.document) ?: return

    // Nothing below runs unless a Figma link meets the change itself, so an edit beside a link that
    // was already there costs nothing and a link typed character by character is answered once. The
    // link here is the matched prefix and stops at `/design/`, so an edit inside that prefix asks
    // again and an edit in the file key after it does not. The platform coalesces the recompute per
    // file (`EditorNotificationsImpl.kt:250-334`).
    if (!changeTouchesFigmaUrl(event.document.immutableCharSequence, event.offset, event.offset + event.newLength)) return
    if (!isMarkdownSuggestionFile(file.path)) return
    if (isFigmaConnectLoaded()) return

    // Every open project the file belongs to. A file outside every content root is edited without a
    // banner until it is opened again, which is the same answer the platform gives such a file for
    // everything else it indexes.
    for (project in ProjectLocator.getInstance().getProjectsForFile(file)) {
      if (project.isDisposed || isFigmaSuggestionDismissed(project)) continue
      // A project showing no editor for this file is answered by `updateNotifications` itself, which
      // stops as soon as it finds none.
      EditorNotifications.getInstance(project).updateNotifications(file)
    }
  }
}
