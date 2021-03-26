// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class XmlElementFactory {
  public static XmlElementFactory getInstance(Project project) {
    return project.getService(XmlElementFactory.class);
  }
  /**
   * Creates an XML text element from the specified string, escaping the special
   * characters in the string as necessary.
   *
   * @param s the text of the element to create.
   * @return the created element.
   * @throws IncorrectOperationException if the creation failed for some reason.
   */
  @NotNull
  public abstract XmlText createDisplayText(@NotNull @NonNls String s) throws IncorrectOperationException;

  /**
   * Creates an XHTML tag with the specified text.
   *
   * @param s the text of an XHTML tag (which can contain attributes and subtags).
   * @return the created tag instance.
   * @throws IncorrectOperationException if the text does not specify a valid XML fragment.
   */
  @NotNull
  public abstract XmlTag createXHTMLTagFromText(@NotNull @NonNls String s) throws IncorrectOperationException;

  /**
   * Creates an HTML tag with the specified text.
   *
   * @param s the text of an HTML tag (which can contain attributes and subtags).
   * @return the created tag instance.
   * @throws IncorrectOperationException if the text does not specify a valid XML fragment.
   */
  @NotNull
  public abstract XmlTag createHTMLTagFromText(@NotNull @NonNls String s) throws IncorrectOperationException;

  /**
   * Creates an XML tag with the specified text.
   *
   * @param text the text of an XML tag (which can contain attributes and subtags).
   * @return the created tag instance.
   * @throws IncorrectOperationException if the text does not specify a valid XML fragment.
   * @see #createTagFromText(CharSequence text, Language language)
   */
  @NotNull
  public abstract XmlTag createTagFromText(@NotNull @NonNls CharSequence text) throws IncorrectOperationException;

  /**
   * Creates XML like tag with the specified text and language.
   *
   * @param text the text of an XML tag (which can contain attributes and subtags).
   * @param language the language for tag to be created.
   * @return the created tag instance.
   * @throws IncorrectOperationException if the text does not specify a valid XML fragment.
   * @see #createTagFromText(CharSequence)
   */
  @NotNull
  public abstract XmlTag createTagFromText(@NotNull @NonNls CharSequence text, @NotNull Language language) throws IncorrectOperationException;

  /**
   * Creates an XML attribute with the specified name and value.
   *
   * @param name  the name of the attribute to create.
   * @param value the value of the attribute to create.
   * @return the created attribute instance.
   * @throws IncorrectOperationException if either {@code name} or {@code value} are not valid.
   */
  @NotNull
  public abstract XmlAttribute createXmlAttribute(@NotNull @NonNls String name, @NotNull String value) throws IncorrectOperationException;

  /**
   * Creates an attribute with the specified name and value  with given context.
   *
   * @param name  the name of the attribute to create.
   * @param value the value of the attribute to create.
   * @param context element which can be used to determine created attribute file type.
   * @return the created attribute instance.
   * @throws IncorrectOperationException if either {@code name} or {@code value} are not valid.
   */
  @NotNull
  public abstract XmlAttribute createAttribute(@NotNull @NonNls String name, @NotNull String value, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates an attribute with the specified name and value quoted with given quotes and with given context.
   *
   * @param name  the name of the attribute to create.
   * @param value the value of the attribute to create.
   * @param quoteStyle preferred quote style.
   * @param context element which can be used to determine created attribute file type.
   * @return the created attribute instance.
   * @throws IncorrectOperationException if either {@code name} or {@code value} are not valid.
   */
  @NotNull
  public abstract XmlAttribute createAttribute(@NotNull @NonNls String name, @NotNull String value, @Nullable Character quoteStyle,
                                               @Nullable PsiElement context) throws IncorrectOperationException;
}
