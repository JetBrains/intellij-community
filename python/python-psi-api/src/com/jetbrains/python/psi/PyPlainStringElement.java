package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstPlainStringElement;

/**
 * Represents a string literal which content in constant (without any interpolation taking place).
 * Namely, these are all kinds of string literals in Python except for f-strings and they map directly
 * to underlying tokens. The following are all examples of such elements:
 *
 * <ul>
 * <li>{@code r'foo\42'}</li>
 * <li><pre><code>
 * b"""\
 * multi
 * line
 * bytes"""</code><pre/></li>
 * <li>{@code '\u0041 \x41 \N{LATIN CAPITAL LETTER A}'}</li>
 * </ul>
 */
public interface PyPlainStringElement extends PyAstPlainStringElement, PyStringElement {
}
