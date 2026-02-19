// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer

import com.intellij.psi.tree.TokenSet
import com.intellij.psi.xml.XmlTokenType

class HtmlRawTextLexer : MergingLexerAdapter(FlexAdapter(_HtmlRawTextLexer()),
                                             TokenSet.create(XmlTokenType.XML_DATA_CHARACTERS))