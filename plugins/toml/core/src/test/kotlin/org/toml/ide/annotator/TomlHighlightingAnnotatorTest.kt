/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.annotator

class TomlHighlightingAnnotatorTest : TomlAnnotatorTestBase(TomlHighlightingAnnotator::class) {

    fun `test numbers`() = checkHighlighting("""
        k1 = <NUMBER>123</NUMBER>
        k2 = <NUMBER>1.0</NUMBER>
        k3 = [ <NUMBER>456</NUMBER>, <NUMBER>789</NUMBER> ]
        k4 = { f1 = <NUMBER>10</NUMBER>, f2 = <NUMBER>20.0</NUMBER> }
    """)

    fun `test dates`() = checkHighlighting("""
        k1 = <DATE>2019-11-08</DATE>
        k2 = [ <DATE>2019-11-04T07:32:00-08:00</DATE>, <DATE>2019-11-08</DATE> ]
        k3 = { date1 = <DATE>2019-11-04</DATE>, date2 = <DATE>2019-11-08</DATE> }
    """)

    fun `test keys`() = checkHighlighting("""
        <KEY>k1</KEY>= "foo"
        <KEY>k2."k3".<KEY>k4</KEY> = "bar"
        <KEY>123</KEY> = "baz"
        <KEY>2019-11-08</KEY> = "qqq"
        <KEY>k5 = { <KEY>k6</KEY> = 123, <KEY>k7</KEY> = 2019-11-08 }
        [<KEY>foo</KEY>.<KEY>bar</KEY>]
    """)

    @BatchMode
    fun `test no highlighting in batch mode`() = checkHighlighting("""
        k1 = 123
        k2 = 1.0
        k3 = [ 2020-07-30 ]
        k4 = { f1 = 10, f2 = 20.0 }
        [foo.bar]
    """, ignoreExtraHighlighting = false)
}
