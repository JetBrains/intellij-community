// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.tokenizer;

import com.intellij.lang.LanguageExtension;

/**
 * @author yole
 */
public class LanguageSpellchecking extends LanguageExtension<SpellcheckingStrategy> {
  public static final LanguageSpellchecking INSTANCE = new LanguageSpellchecking();
  
  private LanguageSpellchecking() {
    super(SpellcheckingStrategy.EP_NAME, new SpellcheckingStrategy());
  }
}
