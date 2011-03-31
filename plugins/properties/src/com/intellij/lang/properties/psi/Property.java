/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 09.04.2005
 * Time: 22:37:50
 */
package com.intellij.lang.properties.psi;

import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see com.intellij.lang.properties.psi.PropertiesElementFactory
 */
public interface Property extends PsiNamedElement, StubBasedPsiElement<PropertyStub>, NavigatablePsiElement {
  @Nullable String getKey();
  @Nullable String getValue();

  /**
   * Returns the value with \n, \r, \t, \f and Unicode escape characters converted to their
   * character equivalents.
   *
   * @return unescaped value, or null if no value is specified for this property.
   */
  @Nullable String getUnescapedValue();

  /**
   * Returns the key with \n, \r, \t, \f and Unicode escape characters converted to their
   * character equivalents.
   *
   * @return unescaped key, or null if no key is specified for this property.
   */
  @Nullable String getUnescapedKey();

  @Nullable String getKeyValueSeparator();

  void setValue(@NonNls @NotNull String value) throws IncorrectOperationException;

  PropertiesFile getContainingFile() throws PsiInvalidElementAccessException;

  /**
   * @return text of comment preceding this property. Comment-start characters ('#' and '!') are stripped from the text.
   */
  @Nullable String getDocCommentText();
}
