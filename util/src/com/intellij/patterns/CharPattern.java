/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class CharPattern extends ObjectPattern<Character, CharPattern> {
  protected CharPattern() {
    super(Character.class);
    
  }

  public CharPattern javaIdentifierPart() {
    return with(new PatternCondition<Character>("javaIdentifierPart") {
      public boolean accepts(@NotNull final Character character,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return Character.isJavaIdentifierPart(character.charValue());
      }
    });
  }
  public CharPattern whitespace() {
    return with(new PatternCondition<Character>("whitespace") {
      public boolean accepts(@NotNull final Character character,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return Character.isWhitespace(character.charValue());
      }
    });
  }

}
