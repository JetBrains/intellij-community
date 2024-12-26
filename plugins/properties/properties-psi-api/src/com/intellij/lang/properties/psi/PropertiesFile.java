// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang.properties.psi;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An interface representing a properties file.
 *
 * It can be both xml based {@link com.intellij.lang.properties.xml.XmlPropertiesFile} or .properties based
 */
public interface PropertiesFile {

  @NotNull
  PsiFile getContainingFile();

  /**
   * @return All properties found in this file.
   */
  @NotNull
  @Unmodifiable
  List<IProperty> getProperties();

  /**
   *
   * @param key the name of the property in the properties file
   * @return property corresponding to the key specified, or null if there is no property found.
   * If there are several properties with the same key, returns first from the top of the file property.
   */
  @Nullable
  IProperty findPropertyByKey(@NotNull @NonNls String key);

  /**
   * @return All properties found in this file with the name specified.
   */
  @NotNull
  @Unmodifiable
  List<IProperty> findPropertiesByKey(@NotNull @NonNls String key);

  @NotNull
  ResourceBundle getResourceBundle();

  @NotNull
  Locale getLocale();

  /**
   * Adds property to the end of the file.
   *
   * @param property to add. Typically you create the property via {@link PropertiesElementFactory}.
   * @return newly added property.
   * It is this value you use to do actual PSI work, e.g. call {@link PsiElement#delete()} to remove this property from the file.
   * @deprecated use {@link #addProperty(String, String)} instead
   * @see #addProperty(String, String)
   */
  @Deprecated(forRemoval = true)
  @NotNull
  PsiElement addProperty(@NotNull IProperty property) throws IncorrectOperationException;

  /**
   * Adds property to the file after the specified property.
   * If anchor is null, property is added to the beginning of the file.
   *
   * In most cases one can consider to use {@link PropertiesFile#addPropertyAfter(String, String, IProperty)} instead of this method.
   *
   * @param property to add. Typically you create the property via {@link PropertiesElementFactory}.
   * @param anchor property after which to add the new property
   * @return newly added property.
   * It is this value you use to do actual PSI work, e.g. call {@link PsiElement#delete()} to remove this property from the file.
   */
  @NotNull
  PsiElement addPropertyAfter(@NotNull IProperty property, @Nullable IProperty anchor) throws IncorrectOperationException;

  /**
   * Adds property to the file after the specified property.
   * If anchor is null, property added to the beginning of the file.
   *
   * @param key of a property to add.
   * @param value of a property to add.
   * @param anchor property after which to add the new property
   * @return newly added property.
   */
  @NotNull
  IProperty addPropertyAfter(@NotNull String key, @NotNull String value, IProperty anchor) throws IncorrectOperationException;

  default @NotNull IProperty addProperty(@NotNull String key, @NotNull String value) {
    return addProperty(key, value, PropertyKeyValueFormat.PRESENTABLE);
  }

  @NotNull
  IProperty addProperty(@NotNull String key, @NotNull String value, @NotNull PropertyKeyValueFormat format);

  /**
   * @return Property key to the property value map.
   * Do not modify this map. It's no use anyway.
   */
  @NotNull
  Map<String,String> getNamesMap();

  @NotNull @NlsSafe
  String getName();

  VirtualFile getVirtualFile();

  /**
   * @return containing directory for the corresponding properties file
   */
  PsiDirectory getParent();

  @NotNull
  Project getProject();

  String getText();

  /**
   * @return true if property keys in file are alphabetically sorted, otherwise returns false
   */
  boolean isAlphaSorted();
}
