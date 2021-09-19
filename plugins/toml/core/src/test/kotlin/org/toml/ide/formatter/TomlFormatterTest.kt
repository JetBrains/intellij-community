/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.formatter

class TomlFormatterTest : TomlFormatterTestBase() {
  fun `test spacing`() = doTest("""
        [  config . subconfig ]
            list  =   [  1,2,     3   ]
        key1 . key2='value'
        object={a=1,b=2}
    """, """
        [config.subconfig]
        list = [1, 2, 3]
        key1.key2 = 'value'
        object = { a = 1, b = 2 }
    """)

  fun `test indent array`() = doTest("""
        [workspace]
        members = [
        "a.rs",
                "b.rs",
                    "c.rs"
        ]
    """, """
        [workspace]
        members = [
            "a.rs",
            "b.rs",
            "c.rs"
        ]
    """)

  fun `test indent table key`() = doOptionTest(tomlSettings()::INDENT_TABLE_KEYS, """
        [workspace]
        key1 = "bar"
        key2 = 5
        key3 = [
            1,
            2
        ]
    """, """
        [workspace]
            key1 = "bar"
            key2 = 5
            key3 = [
                1,
                2
            ]
    """, """
        [workspace]
        key1 = "bar"
        key2 = 5
        key3 = [
            1,
            2
        ]
    """)

  fun `test indent array table key`() = doOptionTest(tomlSettings()::INDENT_TABLE_KEYS, """
        [[workspace]]
        key1 = "bar"
        key2 = 5
        key3 = [
            1,
            2
        ]
    """, """
        [[workspace]]
            key1 = "bar"
            key2 = 5
            key3 = [
                1,
                2
            ]
    """, """
        [[workspace]]
        key1 = "bar"
        key2 = 5
        key3 = [
            1,
            2
        ]
    """)
}
