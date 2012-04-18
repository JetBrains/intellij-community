/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.source.parsing.xml;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerUtil;
import com.intellij.util.CharTable;

/**
 * @author ven
 */
public class XmlParsingContext {
  private final OldXmlParsing myXmlParsing;
  private final CharTable myTable;

  public XmlParsingContext(final CharTable table) {
    myTable = table;
    myXmlParsing = new OldXmlParsing(this);
  }

  public CharTable getCharTable() {
    return myTable;
  }

  public CharSequence tokenText(Lexer lexer) {
    return LexerUtil.internToken(lexer, myTable);
  }

  public OldXmlParsing getXmlParsing() {
    return myXmlParsing;
  }
}
