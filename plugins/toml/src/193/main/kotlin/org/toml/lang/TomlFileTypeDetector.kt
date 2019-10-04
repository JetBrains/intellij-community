/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang

import com.intellij.openapi.fileTypes.FileType
import org.toml.lang.psi.TomlFileType

class TomlFileTypeDetector : TomlFileTypeDetectorBase() {
    override fun getDetectedFileTypes(): Collection<FileType> = listOf(TomlFileType)

    override fun getDesiredContentPrefixLength(): Int = 0
}
