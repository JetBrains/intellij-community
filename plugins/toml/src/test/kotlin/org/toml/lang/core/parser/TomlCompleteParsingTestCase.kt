/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.core.parser

class TomlCompleteParsingTestCase : TomlParsingTestCaseBase("complete") {
    fun testExample() = doTest(true)
    fun testValues() = doTest(true)
}
