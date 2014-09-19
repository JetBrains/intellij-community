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
package com.intellij.embedding;

import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderAdapter;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * A delegate PsiBuilder that hides some tokens (namely, the ones provided by {@link com.intellij.embedding.ForeignTokenClassifierLexer})
 * from a parser, however, _still inserting_ them into a production tree.
 * @see com.intellij.embedding.ForeignTokenClassifierLexer
 */
public class MasqueradingPsiBuilderAdapter extends PsiBuilderAdapter {

  private List<MyShiftedToken> myShrunkSequence;

  private CharSequence myShrunkCharSequence;

  private int myLexPosition;

  public MasqueradingPsiBuilderAdapter(@NotNull final Project project,
                        @NotNull final ParserDefinition parserDefinition,
                        @NotNull final ForeignTokenClassifierLexer lexer,
                        @NotNull final ASTNode chameleon,
                        @NotNull final CharSequence text) {
    super(new PsiBuilderImpl(project, parserDefinition, lexer, chameleon, text));

    initShrunkSequence();
  }

  public MasqueradingPsiBuilderAdapter(@NotNull final Project project,
                        @NotNull final ParserDefinition parserDefinition,
                        @NotNull final ForeignTokenClassifierLexer lexer,
                        @NotNull final LighterLazyParseableNode chameleon,
                        @NotNull final CharSequence text) {
    super(new PsiBuilderImpl(project, parserDefinition, lexer, chameleon, text));

    initShrunkSequence();
  }

  @Override
  public CharSequence getOriginalText() {
    return myShrunkCharSequence;
  }

  @Override
  public void advanceLexer() {
    myLexPosition++;

    synchronizePositions();
  }

  private void synchronizePositions() {
    final PsiBuilder delegate = getDelegate();
    while (!delegate.eof() || myLexPosition < myShrunkSequence.size()) {
      if (myLexPosition >= myShrunkSequence.size()) {
        delegate.advanceLexer();
        continue;
      }
      if (delegate.eof()) {
        myLexPosition = myShrunkSequence.size();
        break;
      }

      final int delegatePosition = delegate.getCurrentOffset();
      final int myPosition = myShrunkSequence.get(myLexPosition).realStart;

      if (delegatePosition < myPosition) {
        delegate.advanceLexer();
      }
      else if (delegatePosition > myPosition) {
        myLexPosition++;
      }
      else {
        break;
      }
    }
  }

  @Override
  public IElementType lookAhead(int steps) {
    final PsiBuilderImpl delegate = (PsiBuilderImpl)getDelegate();

    if (eof()) {    // ensure we skip over whitespace if it's needed
      return null;
    }
    int cur = myLexPosition;

    while (steps > 0) {
      ++cur;
      while (cur < myShrunkSequence.size() && delegate.whitespaceOrComment(myShrunkSequence.get(cur).elementType)) {
        cur++;
      }

      steps--;
    }

    return cur < myShrunkSequence.size() ? myShrunkSequence.get(cur).elementType : null;
  }

  @Override
  public IElementType rawLookup(int steps) {
    int cur = myLexPosition + steps;
    return cur >= 0 && cur < myShrunkSequence.size() ? myShrunkSequence.get(cur).elementType : null;
  }

  @Override
  public int rawTokenTypeStart(int steps) {
    int cur = myLexPosition + steps;
    if (cur < 0) return -1;
    if (cur >= myShrunkSequence.size()) return getOriginalText().length();
    return myShrunkSequence.get(cur).shrunkStart;
  }

  @Override
  public int rawTokenIndex() {
    return myLexPosition;
  }

  @Override
  public int getCurrentOffset() {
    return myLexPosition < myShrunkSequence.size() ? myShrunkSequence.get(myLexPosition).shrunkStart : myShrunkCharSequence.length();
  }

  @Override
  public Marker mark() {
    final Marker mark = super.mark();
    return new MyMarker(mark, myLexPosition);
  }

  protected void initShrunkSequence() {
    final PsiBuilderImpl delegate = (PsiBuilderImpl)getDelegate();
    final ForeignTokenClassifierLexer lexer = (ForeignTokenClassifierLexer)delegate.getLexer();

    initTokenList(lexer);
    initCharSequence();
    myLexPosition = 0;
    synchronizePositions();
  }

  private void initTokenList(ForeignTokenClassifierLexer lexer) {
    lexer.start(getDelegate().getOriginalText());
    myShrunkSequence = new ArrayList<MyShiftedToken>();

    int realPos = 0;
    int shrunkPos = 0;
    IElementType currentTokenType;
    while ((currentTokenType = lexer.getTokenType()) != null) {
      final boolean isForeign = lexer.isForeignToken();

      final int tokenLength = lexer.getTokenEnd() - lexer.getTokenStart();
      if (!isForeign) {
        myShrunkSequence.add(new MyShiftedToken(currentTokenType,
                                                realPos, realPos + tokenLength,
                                                shrunkPos, shrunkPos + tokenLength));
        shrunkPos += tokenLength;
      }
      realPos += tokenLength;

      lexer.advance();
    }
  }

  private void initCharSequence() {
    StringBuilder sb = new StringBuilder();
    for (MyShiftedToken token : myShrunkSequence) {
      sb.append(getDelegate().getOriginalText().subSequence(token.realStart, token.realEnd));
    }
    myShrunkCharSequence = sb.toString();
  }

  @SuppressWarnings({"StringConcatenationInsideStringBufferAppend", "UnusedDeclaration"})
  private void logPos() {
    final Logger log = Logger.getLogger(this.getClass().getSimpleName());
    StringBuilder sb = new StringBuilder();
    sb.append("\nmyLexPosition=" + myLexPosition + "/" + myShrunkSequence.size());
    if (myLexPosition < myShrunkSequence.size()) {
      final MyShiftedToken token = myShrunkSequence.get(myLexPosition);
      sb.append("\nshrunk:" + token.shrunkStart + "," + token.shrunkEnd);
      sb.append("\nreal:" + token.realStart + "," + token.realEnd);
      sb.append("\nTT:" + getTokenText());
    }
    sb.append("\ndelegate:");
    sb.append("eof=" + myDelegate.eof());
    if (!myDelegate.eof()) {
      //noinspection ConstantConditions
      sb.append("\nposition:" + myDelegate.getCurrentOffset() + "," + (myDelegate.getCurrentOffset() + myDelegate.getTokenText().length()));
      sb.append("\nTT:" + myDelegate.getTokenText());
    }
    log.info(sb.toString());
  }


  private static class MyShiftedToken {
    public IElementType elementType;

    public int realStart;
    public int realEnd;

    public int shrunkStart;
    public int shrunkEnd;

    public MyShiftedToken(IElementType elementType, int realStart, int realEnd, int shrunkStart, int shrunkEnd) {
      this.elementType = elementType;
      this.realStart = realStart;
      this.realEnd = realEnd;
      this.shrunkStart = shrunkStart;
      this.shrunkEnd = shrunkEnd;
    }
  }

  private class MyMarker extends DelegateMarker {

    private int myBuilderPosition;

    public MyMarker(Marker delegate, int builderPosition) {
      super(delegate);

      myBuilderPosition = builderPosition;
    }

    @Override
    public void rollbackTo() {
      super.rollbackTo();
      myLexPosition = myBuilderPosition;
    }
  }

  public static class DelegateMarker implements Marker {

    public Marker myDelegate;

    public DelegateMarker(Marker delegate) {
      myDelegate = delegate;
    }

    @Override
    public Marker precede() {
      return myDelegate.precede();
    }

    @Override
    public void drop() {
      myDelegate.drop();
    }

    @Override
    public void rollbackTo() {
      myDelegate.rollbackTo();
    }

    @Override
    public void done(IElementType type) {
      myDelegate.done(type);
    }

    @Override
    public void collapse(IElementType type) {
      myDelegate.collapse(type);
    }

    @Override
    public void doneBefore(IElementType type, Marker before) {
      myDelegate.doneBefore(type, before);
    }

    @Override
    public void doneBefore(IElementType type, Marker before, String errorMessage) {
      myDelegate.doneBefore(type, before, errorMessage);
    }

    @Override
    public void error(String message) {
      myDelegate.error(message);
    }

    @Override
    public void errorBefore(String message, Marker before) {
      myDelegate.errorBefore(message, before);
    }

    @Override
    public void setCustomEdgeTokenBinders(@Nullable WhitespacesAndCommentsBinder left,
                                          @Nullable WhitespacesAndCommentsBinder right) {
      myDelegate.setCustomEdgeTokenBinders(left, right);
    }
  }
}
