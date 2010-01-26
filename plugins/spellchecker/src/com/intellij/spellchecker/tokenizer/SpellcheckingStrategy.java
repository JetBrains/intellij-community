/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.spellchecker.tokenizer;

import com.intellij.lang.Language;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiPlainText;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author shkate@jetbrains.com
 * @author Konstantin Bulenkov
 */
public class SpellcheckingStrategy {
  public static final ExtensionPointName<SpellcheckingStrategy> EP_NAME = ExtensionPointName.create("com.intellij.spellchecker.support");

  @NotNull
  public Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof PsiNameIdentifierOwner) return new PsiIdentifierOwnerTokenizer();
    if (element instanceof PsiComment) return new CommentTokenizer();
    if (element instanceof XmlAttributeValue) return new SimpleTokenizer();
    if (element instanceof XmlText) return new SimpleTokenizer();
    if (element instanceof PropertyValueImpl) return new SimpleTokenizer();
    if (element instanceof PropertyImpl) return new SimpleTokenizer<PropertyImpl>(false, ".") {
      @Nullable
      @Override
      public String getText(PropertyImpl element) {
        return element.getKey();
      }
    };
    if (element instanceof PsiPlainText) return new TextTokenizer();
    return new Tokenizer();
  }

  @NotNull
  public Language getLanguage(){
    return PlainTextLanguage.INSTANCE;
  }
}
