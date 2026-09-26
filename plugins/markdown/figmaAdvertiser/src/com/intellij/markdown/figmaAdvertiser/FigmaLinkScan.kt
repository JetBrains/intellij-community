package com.intellij.markdown.figmaAdvertiser

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileCachedValue
import com.intellij.openapi.vfs.getCachedValue
import org.jetbrains.annotations.ApiStatus

/**
 * Holds the answer [linksToFigma] gave for one file. It is absent for a file that was never read,
 * which is a different state from a file that was read and answered false.
 */
@ApiStatus.Internal
val FIGMA_LINK_SCAN_KEY: Key<VirtualFileCachedValue<Boolean>> =
  Key.create("markdown.figma.link.scan")

/**
 * Whether [file] contains a link to a Figma file.
 *
 * `EditorNotificationsImpl` asks the suggestion providers on a background thread, inside a read
 * action. This reads the file's own text and asks no index and no PSI, so it answers the same way
 * while the index is being built.
 *
 * `VirtualFile.getCachedValue` keeps the answer on the file and drops it when the file's
 * modification stamp or its loaded document's stamp moves, and it reads the loaded document where
 * there is one. The banner is asked about a file an editor is showing, so the usual read costs no
 * I/O. Two calls that race both scan and both store the same answer.
 *
 * The whole text is scanned. A link to a design sits wherever the author put it, so a prefix scan
 * would miss the ones at the bottom of a long document.
 */
@ApiStatus.Internal
fun linksToFigma(file: VirtualFile): Boolean =
  // The length comes from the VFS record, so a file over the cap is never loaded.
  file.length <= MAX_SCANNED_FILE_BYTES &&
  file.getCachedValue(FIGMA_LINK_SCAN_KEY) { _, text -> text != null && containsFigmaUrl(text) }

/**
 * How large a file the banner reads at all, in bytes.
 *
 * A Markdown document a person wrote is far below this. A file above it is generated, and skipping
 * it by its recorded length keeps it out of both the read and the scan.
 */
@ApiStatus.Internal
const val MAX_SCANNED_FILE_BYTES: Long = 1L shl 20
