/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import org.toml.lang.TomlLanguage

class TomlFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, TomlLanguage) {
    override fun getFileType(): FileType = TomlFileType

    override fun toString(): String = "TOML File"
}
