// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("YamlGrammarCharUtil")

package org.jetbrains.yaml.lexer

private const val NS_INDICATORS = "-?:,\\[\\]\\{\\}#&*!|>'\\\"%@`"
private const val NS_FLOW_INDICATORS = ",[]{}"
private const val COMMON_SPACE_CHARS = "\n\r\t "

fun isIndicatorChar(c: Char): Boolean = c in NS_INDICATORS
fun isPlainSafe(c: Char): Boolean = !isSpaceLike(c) && c !in NS_FLOW_INDICATORS
fun isSpaceLike(c: Char): Boolean = c == ' ' || c == '\t'
fun isNonSpaceChar(c: Char): Boolean = c !in COMMON_SPACE_CHARS
