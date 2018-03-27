/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterLazyParseableNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.impl.DelegateMarker;
import com.intellij.lang.impl.PsiBuilderAdapter;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A delegate PsiBuilder that hides or substitutes some tokens (namely, the ones provided by {@link MasqueradingLexer})
 * from a parser, however, _still inserting_ them into a production tree in their initial appearance.
 * @see MasqueradingLexer
 */
public class MasqueradingPsiBuilderAdapter extends PsiBuilderAdapter {
  private final static Logger LOG = Logger.getInstance(MasqueradingPsiBuilderAdapter.class);

  private List<MyShiftedToken> myShrunkSequence;

  private CharSequence myShrunkCharSequence;

  private int myLexPosition;

  private final PsiBuilderImpl myBuilderDelegate;

  private final MasqueradingLexer myLexer;

  public MasqueradingPsiBuilderAdapter(@NotNull final Project project,
                        @NotNull final ParserDefinition parserDefinition,
                        @NotNull final MasqueradingLexer lexer,
                        @NotNull final ASTNode chameleon,
                        @NotNull final CharSequence text) {
    this(new PsiBuilderImpl(project, parserDefinition, lexer, chameleon, text));
  }

  public MasqueradingPsiBuilderAdapter(@NotNull final Project project,
                        @NotNull final ParserDefinition parserDefinition,
                        @NotNull final MasqueradingLexer lexer,
                        @NotNull final LighterLazyParseableNode chameleon,
                        @NotNull final CharSequence text) {
    this(new PsiBuilderImpl(project, parserDefinition, lexer, chameleon, text));
  }

  private MasqueradingPsiBuilderAdapter(PsiBuilderImpl builder) {
    super(builder);

    LOG.assertTrue(myDelegate instanceof PsiBuilderImpl);
    myBuilderDelegate = ((PsiBuilderImpl)myDelegate);

    LOG.assertTrue(myBuilderDelegate.getLexer() instanceof MasqueradingLexer);
    myLexer = ((MasqueradingLexer)myBuilderDelegate.getLexer());

    initShrunkSequence();
  }

  @Override
  public CharSequence getOriginalText() {
    return myShrunkCharSequence;
  }

  @Override
  public void advanceLexer() {
    myLexPosition++;
    skipWhitespace();

    synchronizePositions(false);
  }

  /**
   * @param exact if true then positions should be equal;
   *              else delegate should be behind, not including exactly all foreign (skipped) or whitespace tokens
   */
  private void synchronizePositions(boolean exact) {
    final PsiBuilder delegate = getDelegate();

    if (myLexPosition >= myShrunkSequence.size() || delegate.eof()) {
      myLexPosition = myShrunkSequence.size();
      while (!delegate.eof()) {
        delegate.advanceLexer();
      }
      return;
    }

    if (delegate.getCurrentOffset() > myShrunkSequence.get(myLexPosition).realStart) {
      LOG.error("delegate is ahead of my builder!",
                new Attachment("offset = " + delegate.getCurrentOffset(), getOriginalText().toString()),
                new Attachment("myShrunkSequence", myShrunkSequence.toString())
      );
      return;
    }

    final int keepUpPosition = getKeepUpPosition(exact);

    while (!delegate.eof()) {
      final int delegatePosition = delegate.getCurrentOffset();

      if (delegatePosition < keepUpPosition) {
        delegate.advanceLexer();
      }
      else {
        break;
      }
    }
  }

  private int getKeepUpPosition(boolean exact) {
    if (exact) {
      return myShrunkSequence.get(myLexPosition).realStart;
    }

    int lexPosition = myLexPosition;
    while (lexPosition > 0 && (myShrunkSequence.get(lexPosition - 1).shrunkStart == myShrunkSequence.get(lexPosition).shrunkStart
                               || isWhiteSpaceOnPos(lexPosition - 1))) {
      lexPosition--;
    }
    if (lexPosition == 0) {
      return myShrunkSequence.get(lexPosition).realStart;
    }
    return myShrunkSequence.get(lexPosition - 1).realStart + 1;
  }

  @Override
  public IElementType lookAhead(int steps) {
    if (eof()) {    // ensure we skip over whitespace if it's needed
      return null;
    }
    int cur = myLexPosition;

    while (steps > 0) {
      ++cur;
      while (cur < myShrunkSequence.size() && isWhiteSpaceOnPos(cur)) {
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
    if (eof()) {
      return null;
    }
    skipWhitespace();

    return myLexPosition < myShrunkSequence.size() ? myShrunkSequence.get(myLexPosition).elementType : null;
  }

  @Nullable
  @Override
  public String getTokenText() {
    if (eof()) {
      return null;
    }
    skipWhitespace();

    if (myLexPosition >= myShrunkSequence.size()) {
      return null;
    }

    final MyShiftedToken token = myShrunkSequence.get(myLexPosition);
    return myShrunkCharSequence.subSequence(token.shrunkStart, token.shrunkEnd).toString();
  }

  @Override
  public boolean eof() {
    boolean isEof = myLexPosition >= myShrunkSequence.size();
    if (!isEof) {
      return false;
    }

    synchronizePositions(true);
    return true;
  }

  @NotNull
  @Override
  public Marker mark() {
    Marker originalPositionMarker = null;
    // In the case of the topmost node all should be inserted
    if (myLexPosition != 0) {
      originalPositionMarker = super.mark();
      synchronizePositions(true);
    }

    final Marker mark = super.mark();
    if (myLexPosition == 0) {
      if (myDelegate.getTokenType() == TemplateMasqueradingLexer.MINUS_TYPE) {
        myDelegate.advanceLexer();
      }
    }
    return new MyMarker(mark, originalPositionMarker, myLexPosition);
  }

  private void skipWhitespace() {
    while (myLexPosition < myShrunkSequence.size() && isWhiteSpaceOnPos(myLexPosition)) {
      myLexPosition++;
    }
  }

  private boolean isWhiteSpaceOnPos(int pos) {
    return myBuilderDelegate.whitespaceOrComment(myShrunkSequence.get(pos).elementType);
  }

  protected void initShrunkSequence() {
    initTokenListAndCharSequence(myLexer);
    myLexPosition = 0;
  }

  private void initTokenListAndCharSequence(MasqueradingLexer lexer) {
    lexer.start(getDelegate().getOriginalText());
    myShrunkSequence = new ArrayList<>();
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
    LOG.info(sb.toString());
  }


  private static class MyShiftedToken {
    public final IElementType elementType;

    public final int realStart;
    public final int realEnd;

    public final int shrunkStart;
    public final int shrunkEnd;

    public MyShiftedToken(IElementType elementType, int realStart, int realEnd, int shrunkStart, int shrunkEnd) {
      this.elementType = elementType;
      this.realStart = realStart;
      this.realEnd = realEnd;
      this.shrunkStart = shrunkStart;
      this.shrunkEnd = shrunkEnd;
    }

    @Override
    public String toString() {
      return "MSTk: [" + realStart + ", " + realEnd + "] -> [" + shrunkStart + ", " + shrunkEnd + "]: " + elementType.toString();
    }
  }

  private class MyMarker extends DelegateMarker {

    private final int myBuilderPosition;

    private final Marker myOriginalPositionMarker;

    public MyMarker(Marker delegate, Marker originalPositionMarker, int builderPosition) {
      super(delegate);

      myBuilderPosition = builderPosition;
      myOriginalPositionMarker = originalPositionMarker;
    }

    @Override
    public void rollbackTo() {
      if (myOriginalPositionMarker != null) {
        myOriginalPositionMarker.rollbackTo();
      } else {
        super.rollbackTo();
      }
      myLexPosition = myBuilderPosition;
    }

    @Override
    public void doneBefore(@NotNull IElementType type, @NotNull Marker before) {
      if (myOriginalPositionMarker != null) {
        myOriginalPositionMarker.drop();
      }
      super.doneBefore(type, getDelegateOrThis(before));
    }

    @Override
    public void doneBefore(@NotNull IElementType type, @NotNull Marker before, String errorMessage) {
      if (myOriginalPositionMarker != null) {
        myOriginalPositionMarker.drop();
      }
      super.doneBefore(type, getDelegateOrThis(before), errorMessage);
    }

    @Override
    public void drop() {
      if (myOriginalPositionMarker != null) {
        myOriginalPositionMarker.drop();
      }
      super.drop();
    }

    @Override
    public void done(@NotNull IElementType type) {
      if (myOriginalPositionMarker != null) {
        myOriginalPositionMarker.drop();
      }
      super.done(type);
    }

    @Override
    public void collapse(@NotNull IElementType type) {
      if (myOriginalPositionMarker != null) {
        myOriginalPositionMarker.drop();
      }
      super.collapse(type);
    }

    @Override
    public void error(String message) {
      if (myOriginalPositionMarker != null) {
        myOriginalPositionMarker.drop();
      }
      super.error(message);
    }

    @NotNull
    private Marker getDelegateOrThis(@NotNull Marker marker) {
      if (marker instanceof DelegateMarker) {
        return ((DelegateMarker)marker).getDelegate();
      }
      else {
        return marker;
      }
    }
  }
}
