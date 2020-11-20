/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object TomlIcons {
    val TOML_FILE = load("/icons/toml-file.svg")

    private fun load(path: String): Icon = IconLoader.getIcon(path, TomlIcons::class.java)
}
