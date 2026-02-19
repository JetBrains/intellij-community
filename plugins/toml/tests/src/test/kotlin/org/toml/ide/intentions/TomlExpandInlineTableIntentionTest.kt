/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.intentions

class TomlExpandInlineTableIntentionTest : TomlIntentionTestBase(TomlExpandInlineTableIntention::class) {
    fun `test availability range`() = checkAvailableInSelectionOnly("""
        [dependencies]
        <selection>foo = { version = "0.1.0", features = ["bar"] }</selection>
    """)

    fun `test unavailable in keyvalue with literal`() = doUnavailableTest("""
        [dependencies]
        foo = "0.1.0"<caret>
    """)

    fun `test unavailable in keyvalue with inline array`() = doUnavailableTest("""
        [dependencies]
        foo = []<caret>
    """)

    fun `test bare keyvalue`() = doAvailableTest("""
        foo = { bar = 42 }<caret>
    """,
    """
        [foo]
        bar = 42<caret>
    """)

    fun `test replace simple`() = doAvailableTest("""
        [dependencies]
        foo = { version = "0.1.0" }<caret>
    """, """
        [dependencies]

        [dependencies.foo]
        version = "0.1.0"<caret>
    """)

    fun `test replace from name`() = doAvailableTest("""
        [dependencies]
        <caret>foo = { version = "0.1.0", features = ["bar"] }
    """, """
        [dependencies]

        [dependencies.foo]
        version = "0.1.0"
        features = ["bar"]<caret>
    """)

    fun `test replace from value`() = doAvailableTest("""
        [dependencies]
        foo = { version = "0.1.0", features = ["bar"] }<caret>
    """, """
        [dependencies]

        [dependencies.foo]
        version = "0.1.0"
        features = ["bar"]<caret>
    """)

    fun `test replace from inside keyvalue`() = doAvailableTest("""
        [dependencies]
        foo = { version = "0.1.0", features = ["bar"<caret>] }
    """, """
        [dependencies]

        [dependencies.foo]
        version = "0.1.0"
        features = ["bar"]<caret>
    """)

    fun `test replace with platform-specific table`() = doAvailableTest("""
        [target.'cfg(unix)'.dependencies]
        foo = { version = "0.1.0", features = ["bar"] }<caret>
    """, """
        [target.'cfg(unix)'.dependencies]

        [target.'cfg(unix)'.dependencies.foo]
        version = "0.1.0"
        features = ["bar"]<caret>
    """)

    fun `test replace in the middle`() = doAvailableTest("""
        [dependencies]
        baz = "0.1.0"
        foo = { version = "0.1.0", features = ["bar"] }<caret>
        bar = { version = "0.2.0" }
    """, """
        [dependencies]
        baz = "0.1.0"
        bar = { version = "0.2.0" }

        [dependencies.foo]
        version = "0.1.0"
        features = ["bar"]<caret>
    """)

    fun `test replace with another block`() = doAvailableTest("""
        [dependencies]
        foo = { version = "0.1.0", features = ["bar"] }<caret>

        [features]
        something = []
    """, """
        [dependencies]

        [dependencies.foo]
        version = "0.1.0"
        features = ["bar"]<caret>

        [features]
        something = []
    """)

    fun `test replace in the middle with another block`() = doAvailableTest("""
        [dependencies]
        foo = { version = "0.0.1" }
        bar = { version = "0.0.2" }<caret>
        baz = { version = "0.0.3" }

        [dependencies.quux]
        version = "0.0.4"
    """, """
        [dependencies]
        foo = { version = "0.0.1" }
        baz = { version = "0.0.3" }

        [dependencies.bar]
        version = "0.0.2"<caret>

        [dependencies.quux]
        version = "0.0.4"
    """)

    fun `test with array table`() = doAvailableTest("""
        [[foo]]
        bar = { baz = 42 }<caret>
    """, """
        [[foo]]

        [foo.bar]
        baz = 42
    """)

    fun `test with key segment in table`() = doAvailableTest("""
        [foo.bar]
        baz = { qux = 42 }<caret>
    """, """
        [foo.bar]

        [foo.bar.baz]
        qux = 42
    """)

    fun `test with key segment in key`() = doAvailableTest("""
        [foo]
        bar.baz = { qux = 42 }<caret>
    """, """
        [foo]

        [foo.bar.baz]
        qux = 42
    """)

    fun `test nested inline tables`() = doAvailableTest("""
        [foo]
        bar = { baz = { qux = 42 } }<caret>
    """, """
        [foo]

        [foo.bar]
        baz = { qux = 42 }<caret>
    """)
}
