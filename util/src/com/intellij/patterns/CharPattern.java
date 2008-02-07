/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import org.jetbrains.annotations.NotNull;
import com.intellij.util.ProcessingContext;

/**
 * @author peter
 */
public class CharPattern extends ObjectPattern<Character, CharPattern> {
  protected CharPattern() {
    super(Character.class);
    
  }

  public CharPattern javaIdentifierPart() {
    return with(new PatternCondition<Character>("javaIdentifierPart") {
      public boolean accepts(@NotNull final Character character, final ProcessingContext context) {
        return Character.isJavaIdentifierPart(character.charValue());
      }
    });
  }
  public CharPattern whitespace() {
    return with(new PatternCondition<Character>("whitespace") {
      public boolean accepts(@NotNull final Character character, final ProcessingContext context) {
        return Character.isWhitespace(character.charValue());
      }
    });
  }

}
