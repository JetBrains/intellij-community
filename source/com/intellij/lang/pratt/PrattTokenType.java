/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import com.intellij.lang.Language;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class PrattTokenType extends IElementType {
  private final Nud myNud;
  private final Led myLed;
  private final int myPriority;

  public PrattTokenType(@NotNull final String tokenName,
                          @Nullable final Language language, final int priority, @NotNull final Nud nud, @NotNull final Led led) {
    super(tokenName, language);
    myPriority = priority;
    myNud = nud;
    myLed = led;
  }

  public PrattTokenType(@NotNull final String tokenName,
                          @Nullable final Language language, final int priority, @NotNull final Nud nud) {
    this(tokenName, language, priority, nud, Led.EMPTY);
  }

  public PrattTokenType(@NotNull final String tokenName,
                          @Nullable final Language language, final int priority, @NotNull final Led led) {
    this(tokenName, language, priority, Nud.EMPTY, led);
  }

  public PrattTokenType(@NotNull final String tokenName,
                          @Nullable final Language language, final int priority) {
    this(tokenName, language, priority, Nud.EMPTY, Led.EMPTY);
  }

  public PrattTokenType(@NotNull final String tokenName,
                          @Nullable final Language language, @NotNull final Nud nud, @NotNull final Led led) {
    this(tokenName, language, 0, nud, led);
  }

  public PrattTokenType(@NotNull final String tokenName,
                          @Nullable final Language language, @NotNull final Nud nud) {
    this(tokenName, language, nud, Led.EMPTY);
  }

  public PrattTokenType(@NotNull final String tokenName,
                          @Nullable final Language language, @NotNull final Led led) {
    this(tokenName, language, Nud.EMPTY, led);
  }

  public String getExpectedText() {
    return PsiBundle.message("0.expected", toString());
  }

  public final int getPriority() {
    return myPriority;
  }

  @Nullable
  public final IElementType parseInfix(final IElementType left, final PrattBuilder builder) {
    return myLed.parseInfix(left, builder);
  }

  @Nullable
  public final IElementType parsePrefix(final PrattBuilder builder) {
    return myNud.parsePrefix(builder);
  }
}
