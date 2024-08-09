/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil
import org.toml.TomlTestBase
import org.toml.lang.psi.TomlFileType

class TomlFileTypeTest : TomlTestBase() {

    fun `test toml file by extension`() = doTest("example.toml")
    fun `test Cargo lock`() = doTest("Cargo.lock")
    fun `test Cargo toml orig`() = doTest("Cargo.toml.orig")
    fun `test Gopkg lock`() = doTest("Gopkg.lock")
    fun `test PDM lock file`() = doTest("pdm.lock")
    fun `test Pipfile`() = doTest("Pipfile")
    fun `test Poetry lock file`() = doTest("poetry.lock")
    fun `test Cargo config`() = doTest(".cargo/config")

    private fun doTest(path: String) {
        val root = myFixture.findFileInTempDir(".")
        val dirPath = path.substringBeforeLast(VfsUtil.VFS_SEPARATOR_CHAR)
        val fileName = path.substringAfterLast(VfsUtil.VFS_SEPARATOR_CHAR)
        val file = runWriteAction {
            val dir = VfsUtil.createDirectoryIfMissing(root, dirPath)
            dir.createChildData(this, fileName).also {
                VfsUtil.saveText(it, "a = 123")
            }
        }
        assertEquals(TomlFileType, file.fileType)
    }
}
