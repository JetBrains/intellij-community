// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.colors.pages;

import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.xml.XmlCoreBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

public class XMLColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[] {
    new AttributesDescriptor(XmlCoreBundle.message("options.xml.attribute.descriptor.prologue"), XmlHighlighterColors.XML_PROLOGUE),
    new AttributesDescriptor(XmlCoreBundle.message("options.xml.attribute.descriptor.comment"), XmlHighlighterColors.XML_COMMENT),
    new AttributesDescriptor(XmlCoreBundle.message("options.xml.attribute.descriptor.tag"), XmlHighlighterColors.XML_TAG),
    new AttributesDescriptor(XmlCoreBundle.message("options.xml.attribute.descriptor.tag.name"), XmlHighlighterColors.XML_TAG_NAME),
    new AttributesDescriptor(XmlCoreBundle.message("options.xml.attribute.descriptor.tag.name.custom"), XmlHighlighterColors.XML_CUSTOM_TAG_NAME),
    new AttributesDescriptor(XmlCoreBundle.message("options.xml.attribute.descriptor.matched.tag.name"), XmlHighlighterColors.MATCHED_TAG_NAME),
    new AttributesDescriptor(XmlCoreBundle.message("options.xml.attribute.descriptor.namespace.prefix"), XmlHighlighterColors.XML_NS_PREFIX),
    new AttributesDescriptor(XmlCoreBundle.message("options.xml.attribute.descriptor.attribute.name"), XmlHighlighterColors.XML_ATTRIBUTE_NAME),
    new AttributesDescriptor(XmlCoreBundle.message("options.xml.attribute.descriptor.attribute.value"), XmlHighlighterColors.XML_ATTRIBUTE_VALUE),
    new AttributesDescriptor(XmlCoreBundle.message("options.xml.attribute.descriptor.tag.data"), XmlHighlighterColors.XML_TAG_DATA),
    new AttributesDescriptor(XmlCoreBundle.message("options.xml.attribute.descriptor.descriptor.entity,reference"), XmlHighlighterColors.XML_ENTITY_REFERENCE),
    new AttributesDescriptor(OptionsBundle.message("options.any.color.descriptor.injected.language.fragment"), XmlHighlighterColors.XML_INJECTED_LANGUAGE_FRAGMENT),
  };

  @Override
  public @NotNull String getDisplayName() {
    return XmlCoreBundle.message("options.xml.display.name");
  }

  @Override
  public Icon getIcon() {
    return XmlFileType.INSTANCE.getIcon();
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
  public @NotNull SyntaxHighlighter getHighlighter() {
    return new XmlFileHighlighter();
  }

  @Override
  public @NotNull String getDemoText() {
    return """
      <?xml version='1.0' encoding='ISO-8859-1'  ?>
      <!DOCTYPE index>
      <!-- Some xml example -->
      <index version="1.0" xmlns:<bg><np>pf</np></bg>="http://test">
         <name>Main Index</name>
         <indexitem text="rename" target="refactoring.rename"/>
         <indexitem text="move" target="refactoring.move"/>
         <indexitem text="migrate" target="refactoring.migrate"/>
         <indexitem text="usage search" target="find.findUsages"/>
         <<matched>indexitem</matched>>Matched tag name</<matched>indexitem</matched>>
         <someTextWithEntityRefs>&amp; &#x00B7;</someTextWithEntityRefs>
         <withCData><![CDATA[
                <object class="MyClass" key="constant">
                </object>
              ]]>
         </withCData>
         <indexitem text="project" target="project.management"/>
         <<custom_tag_name>custom-tag</custom_tag_name>>hello</<custom_tag_name>custom-tag</custom_tag_name>>
         <<bg><np>pf</np></bg>:foo <bg><np>pf</np></bg>:bar="bar"/>
      </index>""";
  }

  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return Map.of(
      "custom_tag_name", XmlHighlighterColors.XML_CUSTOM_TAG_NAME,
      "np", XmlHighlighterColors.XML_NS_PREFIX,
      "bg", XmlHighlighterColors.XML_TAG,
      "matched", XmlHighlighterColors.MATCHED_TAG_NAME);
  }
}
