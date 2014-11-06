/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.buildout.config;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class BuildoutCfgParser implements PsiParser, BuildoutCfgElementTypes, BuildoutCfgTokenTypes {
  @NotNull
  public ASTNode parse(IElementType root, PsiBuilder builder) {
    final PsiBuilder.Marker rootMarker = builder.mark();
    Parsing parsing = new Parsing(builder);
    while (!builder.eof()) {
      if (!parsing.parseSection()) {
        parsing.skipLine();
      }
    }
    rootMarker.done(root);
    return builder.getTreeBuilt();
  }

  private class Parsing {
    private final PsiBuilder myBuilder;

    public Parsing(PsiBuilder builder) {
      myBuilder = builder;
    }

    public boolean parseSection() {
      PsiBuilder.Marker section = mark();

      boolean res = true;

      PsiBuilder.Marker sectionHeader = mark();
      if (parseSectionHeader()) {
        sectionHeader.done(SECTION_HEADER);
      }
      else {
        error("Section name expected.");
        sectionHeader.drop();
        res = false;
      }

      while (is(KEY_CHARACTERS)) {
        res = true;
        parseOption();
      }
      section.done(SECTION);

      if (is(VALUE_CHARACTERS)) {
        advance();
        error("Key expected.");
      }
      while (is(VALUE_CHARACTERS)) {
        skipLine();
      }

      return res;
    }

    private void parseOption() {
      PsiBuilder.Marker option = myBuilder.mark();
      if (is(KEY_CHARACTERS)) {
        doneAdvance(mark(), KEY);
      }
      if (is(KEY_VALUE_SEPARATOR)) {
        advance();
      }
      else {
        error(": or = expected.");
      }

      PsiBuilder.Marker value = mark();
      while (is(VALUE_CHARACTERS)) {
        doneAdvance(mark(), VALUE_LINE);
      }
      value.done(VALUE);
      option.done(OPTION);
    }

    private boolean parseSectionHeader() {
      if (is(LBRACKET)) {

        advance();
        PsiBuilder.Marker sectionName = mark();
        boolean flag = false;
        while (is(SECTION_NAME)) {
          advance();
          flag = true;
        }

        if (flag) {
          sectionName.done(SECTION_NAME);
        }
        else {
          sectionName.drop();
          return false;
        }

        if (is(RBRACKET)) {
          advance();
        }
        else {
          error("] expected.");
          skipLine();
        }

        return true;
      }
      return false;
    }

    private boolean is(IElementType type) {
      return myBuilder.getTokenType() == type;
    }

    private void error(String message) {
      myBuilder.error(message);
    }

    private PsiBuilder.Marker mark() {
      return myBuilder.mark();
    }


    public void doneAdvance(PsiBuilder.Marker marker, IElementType element) {
      myBuilder.advanceLexer();
      marker.done(element);
    }

    public void advance() {
      myBuilder.advanceLexer();
    }

    private void skipLine() {
      while (!is(null) && !is(LBRACKET) && !is(KEY_CHARACTERS)) {
        myBuilder.advanceLexer();
      }
    }
  }
}
