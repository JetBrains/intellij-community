/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang

import com.intellij.openapi.fileTypes.ExactFileNameMatcher
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeConsumer
import com.intellij.openapi.fileTypes.FileTypeFactory
import org.toml.lang.psi.TomlFileType

class TomlFileTypeFactory : FileTypeFactory() {
    override fun createFileTypes(consumer: FileTypeConsumer) {
        consumer.consume(TomlFileType,
            ExactFileNameMatcher("Cargo.lock"),
            ExactFileNameMatcher("Gopkg.lock"),
            ExactFileNameMatcher("Pipfile"),
            ExtensionFileNameMatcher(TomlFileType.defaultExtension))
    }
}
