/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml

interface TomlTestCase : TestCase {
    override val testFileExtension: String get() = "toml"
}
