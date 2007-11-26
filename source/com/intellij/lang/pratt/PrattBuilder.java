/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

/**
 * @author peter
 */
public class PrattBuilder {
  private final PsiBuilder myBuilder;
  private PsiBuilder.Marker myPrevMarker;

  public PrattBuilder(final PsiBuilder builder) {
    myBuilder = builder;
  }

  @NotNull
  public PsiBuilder.Marker markBefore() {
    final PsiBuilder.Marker marker = myPrevMarker;
    myPrevMarker = myPrevMarker.precede();
    return marker;
  }

  public void parseUnless(int rbp, PrattTokenType... endTypes) {
    final Set<IElementType> set = new THashSet<IElementType>();
    set.addAll(Arrays.asList(endTypes));
    while (true) {
      final IElementType tokenType = getTokenType();
      if (tokenType == null) {
        assertToken(endTypes[0]);
        return;
      }
      if (set.contains(tokenType)) {
        advance();
        return;
      }
      parse(rbp);
    }
  }

  @Nullable
  public IElementType parseOption(int rightPriority) {
    final PsiBuilder.Marker marker = mark();
    final IElementType type = parse(rightPriority);
    if (type == null) {
      marker.rollbackTo();
    } else {
      marker.drop();
    }
    return type;
  }

  @Nullable
  public IElementType parse(int rightPriority) {
    IElementType tokenType = getTokenType();
    if (tokenType == null) return null;

    PsiBuilder.Marker marker = myBuilder.mark();
    myBuilder.advanceLexer();
    IElementType left = tokenType instanceof PrattTokenType ? ((PrattTokenType)tokenType).parsePrefix(this) : null;

    while (left != null) {
      final PsiBuilder.Marker marker1 = marker.precede();
      marker.done(left);
      marker = marker1;

      tokenType = myBuilder.getTokenType();
      if (tokenType == null || tokenType instanceof PrattTokenType && rightPriority >= ((PrattTokenType)tokenType).getPriority()) break;

      final PsiBuilder.Marker oldMarker = myPrevMarker;
      myPrevMarker = myBuilder.mark();
      try {
        myBuilder.advanceLexer();
        if (!(tokenType instanceof PrattTokenType)) break;

        left = ((PrattTokenType)tokenType).parseInfix(left, this);
      }
      finally {
        myPrevMarker.drop();
        myPrevMarker = oldMarker;
      }

    }
    marker.drop();

    return left;
  }

  public boolean assertToken(final PrattTokenType type) {
    return checkToken(type, true);
  }

  public PsiBuilder.Marker mark() {
    return myBuilder.mark();
  }

  public boolean checkToken(final PrattTokenType type, final boolean error) {
    if (getTokenType() == type) {
      advance();
      return true;
    }
    if (error) {
      myBuilder.error(type.getExpectedText());
    }
    return false;
  }

  private void advance() {
    myBuilder.advanceLexer();
  }


  public boolean checkToken(final Class<? extends PrattTokenType> type, @Nullable String errorText) {
    if (type.isInstance(getTokenType())) {
      advance();
      return true;
    }
    if (errorText != null) {
      myBuilder.error(errorText);
    }
    return false;
  }


  public boolean isEof() {
    return getTokenType() == null;
  }

  @Nullable
  private IElementType getTokenType() {
    return myBuilder.getTokenType();
  }

  public ASTNode getTreeBuilt() {
    return myBuilder.getTreeBuilt().getFirstChildNode();
  }
}
