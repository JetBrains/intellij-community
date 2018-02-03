/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.io.ByteSequence
import com.intellij.openapi.vfs.VirtualFile
import org.toml.lang.psi.TomlFileType

class TomlFileTypeDetector : FileTypeRegistry.FileTypeDetector {
    override fun getVersion(): Int = 1

    override fun detect(file: VirtualFile, firstBytes: ByteSequence, firstCharsIfText: CharSequence?): FileType? =
        if (file.name == "config" && file.parent?.name == ".cargo") TomlFileType else null
}
