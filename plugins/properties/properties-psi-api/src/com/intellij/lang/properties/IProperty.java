/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An interface representing a generic property. It can be a property inside xml file or a property inside .properties-file
 *
 */
public interface IProperty extends Navigatable, Iconable {
  IProperty[] EMPTY_ARRAY = new IProperty[0];
  DataKey<IProperty[]> ARRAY_KEY = DataKey.create("IProperty.array");

  String getName();

  PsiElement setName(String name);

  @Nullable
  String getKey();

  @Nullable
  String getValue();

  /**
   * Returns the value with \n, \r, \t, \f and Unicode escape characters converted to their
   * character equivalents.
   *
   * @return unescaped value, or null if no value is specified for this property.
   */
  @Nullable
  String getUnescapedValue();

  /**
   * Returns the key with \n, \r, \t, \f and Unicode escape characters converted to their
   * character equivalents.
   *
   * @return unescaped key, or null if no key is specified for this property.
   */
  @Nullable
  String getUnescapedKey();

  void setValue(@NonNls @NotNull String value) throws IncorrectOperationException;

  PropertiesFile getPropertiesFile() throws PsiInvalidElementAccessException;

  /**
   * @return text of comment preceding this property. Comment-start characters ('#' and '!') are stripped from the text.
   */
  @Nullable
  String getDocCommentText();

  /**
   * @return underlying psi element of property
   */
  @NotNull
  PsiElement getPsiElement();
}
