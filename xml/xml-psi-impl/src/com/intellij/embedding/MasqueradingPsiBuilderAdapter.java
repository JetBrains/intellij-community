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
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * A delegate PsiBuilder that hides or substitutes some tokens (namely, the ones provided by {@link MasqueradingLexer})
 * from a parser, however, _still inserting_ them into a production tree in their initial appearance.
 * @see MasqueradingLexer
 */
public class MasqueradingPsiBuilderAdapter extends PsiBuilderAdapter {

  private List<MyShiftedToken> myShrunkSequence;

  private CharSequence myShrunkCharSequence;

  private int myLexPosition;

  public MasqueradingPsiBuilderAdapter(@NotNull final Project project,
                        @NotNull final ParserDefinition parserDefinition,
                        @NotNull final MasqueradingLexer lexer,
                        @NotNull final ASTNode chameleon,
                        @NotNull final CharSequence text) {
    super(new PsiBuilderImpl(project, parserDefinition, lexer, chameleon, text));

    initShrunkSequence();
  }

  public MasqueradingPsiBuilderAdapter(@NotNull final Project project,
                        @NotNull final ParserDefinition parserDefinition,
                        @NotNull final MasqueradingLexer lexer,
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
//    logPos();
    myLexPosition++;

    synchronizePositions();
//    logPos();
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
    synchronizePositions();

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

  @Nullable
  @Override
  public IElementType getTokenType() {
    if (allIsEmpty()) {
      return TokenType.DUMMY_HOLDER;
    }
    checkWhitespace();

    return myLexPosition < myShrunkSequence.size() ? myShrunkSequence.get(myLexPosition).elementType : null;
  }

  @Nullable
  @Override
  public String getTokenText() {
    if (allIsEmpty()) {
      return getDelegate().getOriginalText().toString();
    }
    checkWhitespace();

    if (myLexPosition >= myShrunkSequence.size()) {
      return null;
    }

    final MyShiftedToken token = myShrunkSequence.get(myLexPosition);
    return myShrunkCharSequence.subSequence(token.shrunkStart, token.shrunkEnd).toString();
  }

  @Override
  public Marker mark() {
    final Marker mark = super.mark();
    return new MyMarker(mark, myLexPosition);
  }

  private boolean allIsEmpty() {
    return myShrunkSequence.isEmpty() && getDelegate().getOriginalText().length() != 0;
  }

  private void checkWhitespace() {
    while (myLexPosition < myShrunkSequence.size() &&
           ((PsiBuilderImpl)myDelegate).whitespaceOrComment(myShrunkSequence.get(myLexPosition).elementType)) {
      myLexPosition++;
    }
    synchronizePositions();
  }

  protected void initShrunkSequence() {
    final PsiBuilderImpl delegate = (PsiBuilderImpl)getDelegate();
    final MasqueradingLexer lexer = (MasqueradingLexer)delegate.getLexer();

    initTokenListAndCharSequence(lexer);
    myLexPosition = 0;
//    synchronizePositions();
  }

  private void initTokenListAndCharSequence(MasqueradingLexer lexer) {
    lexer.start(getDelegate().getOriginalText());
    myShrunkSequence = new ArrayList<MyShiftedToken>();
    StringBuilder charSequenceBuilder = new StringBuilder();

    int realPos = 0;
    int shrunkPos = 0;
    while (lexer.getTokenType() != null) {
      final IElementType masqueTokenType = lexer.getMasqueTokenType();
      final String masqueTokenText = lexer.getMasqueTokenText();

      final int realLength = lexer.getTokenEnd() - lexer.getTokenStart();
      if (masqueTokenType != null) {
        assert masqueTokenText != null;

        final int masqueLength = masqueTokenText.length();
        myShrunkSequence.add(new MyShiftedToken(masqueTokenType,
                                                realPos, realPos + realLength,
                                                shrunkPos, shrunkPos + masqueLength));
        charSequenceBuilder.append(masqueTokenText);

        shrunkPos += masqueLength;
      }
      realPos += realLength;

      lexer.advance();
    }

    myShrunkCharSequence = charSequenceBuilder.toString();
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
