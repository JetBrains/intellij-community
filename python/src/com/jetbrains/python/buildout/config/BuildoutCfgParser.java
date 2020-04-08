// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class BuildoutCfgParser implements PsiParser, BuildoutCfgElementTypes, BuildoutCfgTokenTypes {
  @Override
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

  private static class Parsing {
    private final PsiBuilder myBuilder;

    Parsing(PsiBuilder builder) {
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

    private void error(@NotNull String message) {
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
