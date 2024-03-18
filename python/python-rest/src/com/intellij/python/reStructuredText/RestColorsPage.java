// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * User : catherine
 */
public class RestColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[]{
    new AttributesDescriptor(RestBundle.message("colors.page.attributes.descriptor.comment"), RestSyntaxHighlighter.REST_COMMENT),
    new AttributesDescriptor(RestBundle.message("colors.page.attributes.descriptor.title"), RestSyntaxHighlighter.REST_SECTION_HEADER),
    new AttributesDescriptor(RestBundle.message("explicit.markup"), RestSyntaxHighlighter.REST_EXPLICIT),
    new AttributesDescriptor(RestBundle.message("colors.page.attributes.descriptor.fields"), RestSyntaxHighlighter.REST_FIELD),
    new AttributesDescriptor(RestBundle.message("reference.name"), RestSyntaxHighlighter.REST_REF_NAME),
    new AttributesDescriptor(RestBundle.message("inline.literals"), RestSyntaxHighlighter.REST_FIXED),
    new AttributesDescriptor(RestBundle.message("bold.text"), RestSyntaxHighlighter.REST_BOLD),
    new AttributesDescriptor(RestBundle.message("italic.text"), RestSyntaxHighlighter.REST_ITALIC),
    new AttributesDescriptor(RestBundle.message("interpreted.text"), RestSyntaxHighlighter.REST_INTERPRETED),
    new AttributesDescriptor(RestBundle.message("literal.and.line.blocks"), RestSyntaxHighlighter.REST_INLINE),
  };

  @NonNls private static final HashMap<String, TextAttributesKey> ourTagToDescriptorMap = new HashMap<>();


  @Override
  @NotNull
  public String getDisplayName() {
    return RestBundle.message("restructured.text");
  }

  @Override
  public Icon getIcon() {
    return RestFileType.INSTANCE.getIcon();
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return ATTRS;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public SyntaxHighlighter getHighlighter() {
    final SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(RestFileType.INSTANCE, null, null);
    assert highlighter != null;
    return highlighter;
  }

  @Override
  @NotNull
  public String getDemoText() {
    return
      """
        .. comment for documentation master file

        ===============
         Section Title
        ===============

        .. toctree::
           :maxdepth: 2

        There is *some italics text*
        and **bold one** and ``inline literals``

        A link_ in citation style.

        .. _link: http://www.google.com

        .. rubric:: Footnotes
        .. [*] footnote.
        .. [REL09] Citation

        :ref:`builders`


        ::

          Whitespace, newlines, blank lines, and
          all kinds of markup (like *this* or
          \\this) is preserved by literal blocks.

        It was literal block.""";
  }

  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ourTagToDescriptorMap;
  }
}
