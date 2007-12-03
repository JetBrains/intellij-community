/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    if (!(tokenType instanceof PrattTokenType) || ((PrattTokenType)tokenType).getPriority() <= rightPriority) {
      myBuilder.error(JavaErrorMessages.message("unexpected.token"));
      return null;
    }

    final Nud nud = ((PrattTokenType)tokenType).getNud();
    if (nud == null) {
      myBuilder.error(JavaErrorMessages.message("unexpected.token"));
      return null;
    }

    PsiBuilder.Marker marker = myBuilder.mark();

    ParseResult left;
    IElementType result;

    final PsiBuilder.Marker oldMarker = myPrevMarker;
    myPrevMarker = myBuilder.mark();
    try {
      myBuilder.advanceLexer();
      left = nud.parsePrefix(this);
      result = left.getDoneType();
    }
    finally {
      myPrevMarker.drop();
      myPrevMarker = oldMarker;
    }

    while (!left.isError()) {
      tokenType = myBuilder.getTokenType();
      if (!(tokenType instanceof PrattTokenType) || rightPriority >= ((PrattTokenType)tokenType).getPriority()) break;

      final Led led = ((PrattTokenType)tokenType).getLed();
      if (led == null) break;

      myPrevMarker = myBuilder.mark();
      try {
        myBuilder.advanceLexer();

        left = led.parseInfix(result, this);

        if (result != null && left.getDoneType() != null) {
          final PsiBuilder.Marker marker1 = marker.precede();
          marker.doneBefore(result, myPrevMarker);
          marker = marker1;
          result = left.getDoneType();
        }
      }
      finally {
        myPrevMarker.drop();
        myPrevMarker = oldMarker;
      }

    }
    if (result != null) {
      marker.done(result);
    } else {
      marker.drop();
    }

    return result;
  }

  public boolean assertToken(final PrattTokenType type) {
    return _checkToken(type, true);
  }

  public PsiBuilder.Marker mark() {
    return myBuilder.mark();
  }

  public boolean checkToken(final PrattTokenType type) {
    return _checkToken(type, false);
  }

  private boolean _checkToken(final PrattTokenType type, final boolean error) {
    if (isToken(type)) {
      advance();
      return true;
    }
    if (error) {
      myBuilder.error(type.getExpectedText());
    }
    return false;
  }

  public void advance() {
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
    return isToken(null);
  }

  public boolean isToken(@Nullable IElementType type) {
    return getTokenType() == type;
  }

  @Nullable
  public IElementType getTokenType() {
    return myBuilder.getTokenType();
  }

  public ASTNode getTreeBuilt() {
    return myBuilder.getTreeBuilt().getFirstChildNode();
  }
}
