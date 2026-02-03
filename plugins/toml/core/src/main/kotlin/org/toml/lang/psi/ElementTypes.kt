/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.toml.TomlBundle
import org.toml.TomlIcons
import org.toml.lang.TomlLanguage
import org.toml.lang.psi.TomlElementTypes.BASIC_STRING
import org.toml.lang.psi.TomlElementTypes.BOOLEAN
import org.toml.lang.psi.TomlElementTypes.COMMENT
import org.toml.lang.psi.TomlElementTypes.DATE_TIME
import org.toml.lang.psi.TomlElementTypes.LITERAL_STRING
import org.toml.lang.psi.TomlElementTypes.MULTILINE_BASIC_STRING
import org.toml.lang.psi.TomlElementTypes.MULTILINE_LITERAL_STRING
import org.toml.lang.psi.TomlElementTypes.NUMBER
import javax.swing.Icon

class TomlTokenType(debugName: String) : IElementType(debugName, TomlLanguage)
class TomlCompositeType(debugName: String) : IElementType(debugName, TomlLanguage)

object TomlFileType : LanguageFileType(TomlLanguage), FileTypeIdentifiableByVirtualFile {
    override fun getName(): String = "TOML"
    override fun getDescription(): String = TomlBundle.message("filetype.toml.description")
    override fun getDefaultExtension(): String = "toml"
    override fun getIcon(): Icon = TomlIcons.TomlFile
    override fun getCharset(file: VirtualFile, content: ByteArray): String = "UTF-8"

    override fun isAvailableForOverride(): Boolean = true

    override fun isMyFileType(file: VirtualFile): Boolean {
        return StringUtil.equal(file.nameSequence, "config", true) && file.parent?.name == ".cargo"
    }
}

val TOML_COMMENTS: TokenSet = TokenSet.create(COMMENT)

val TOML_BASIC_STRINGS: TokenSet = TokenSet.create(BASIC_STRING, MULTILINE_BASIC_STRING)
val TOML_LITERAL_STRINGS: TokenSet = TokenSet.create(LITERAL_STRING, MULTILINE_LITERAL_STRING)
val TOML_SINGLE_LINE_STRINGS: TokenSet = TokenSet.create(BASIC_STRING, LITERAL_STRING)
val TOML_MULTILINE_STRINGS: TokenSet = TokenSet.create(MULTILINE_BASIC_STRING, MULTILINE_LITERAL_STRING)
val TOML_STRING_LITERALS: TokenSet = TokenSet.orSet(TOML_BASIC_STRINGS, TOML_LITERAL_STRINGS)
val TOML_LITERALS: TokenSet = TokenSet.orSet(
    TOML_STRING_LITERALS,
    TokenSet.create(
        BOOLEAN,
        NUMBER,
        DATE_TIME
    )
)
