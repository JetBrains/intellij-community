/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.toml.ide.icons.TomlIcons
import org.toml.lang.TomlLanguage
import org.toml.lang.psi.TomlElementTypes.*
import javax.swing.Icon

class TomlTokenType(debugName: String) : IElementType(debugName, TomlLanguage)
class TomlCompositeType(debugName: String) : IElementType(debugName, TomlLanguage)

object TomlFileType : LanguageFileType(TomlLanguage), FileTypeIdentifiableByVirtualFile {
    override fun getName(): String = "TOML"
    override fun getDescription(): String = "TOML file"
    override fun getDefaultExtension(): String = "toml"
    override fun getIcon(): Icon = TomlIcons.TOML_FILE
    override fun getCharset(file: VirtualFile, content: ByteArray): String = "UTF-8"

    override fun isMyFileType(file: VirtualFile): Boolean {
        return file.name == "config" && file.parent?.name == ".cargo"
    }
}

val TOML_COMMENTS = TokenSet.create(COMMENT)

val TOML_BASIC_STRINGS = TokenSet.create(BASIC_STRING, MULTILINE_BASIC_STRING)
val TOML_LITERAL_STRINGS = TokenSet.create(LITERAL_STRING, MULTILINE_LITERAL_STRING)
val TOML_STRING_LITERALS = TokenSet.orSet(TOML_BASIC_STRINGS, TOML_LITERAL_STRINGS)
val TOML_LITERALS = TokenSet.orSet(
    TOML_STRING_LITERALS,
    TokenSet.create(
        BOOLEAN,
        NUMBER,
        DATE_TIME
    )
)
