/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

// BACKCOMPAT 2019.1
@file:Suppress("DEPRECATION")

package org.toml.lang

import com.intellij.openapi.fileTypes.ExactFileNameMatcher
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeConsumer
import com.intellij.openapi.fileTypes.FileTypeFactory
import org.toml.lang.psi.TomlFileType

// BACKCOMPAT 2019.1. Use `fileType` extension
//  Make sure that TOML plugin since version >= 192 at TeamCity
class TomlFileTypeFactory : FileTypeFactory() {
    override fun createFileTypes(consumer: FileTypeConsumer) {
        consumer.consume(TomlFileType,
            ExactFileNameMatcher("Cargo.lock"),
            ExactFileNameMatcher("Gopkg.lock"),
            ExactFileNameMatcher("Pipfile"),
            ExtensionFileNameMatcher(TomlFileType.defaultExtension))
    }
}
