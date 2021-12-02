/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

public class XMLColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[] {
    new AttributesDescriptor(XmlBundle.message("options.xml.attribute.descriptor.prologue"), XmlHighlighterColors.XML_PROLOGUE),
    new AttributesDescriptor(XmlBundle.message("options.xml.attribute.descriptor.comment"), XmlHighlighterColors.XML_COMMENT),
    new AttributesDescriptor(XmlBundle.message("options.xml.attribute.descriptor.tag"), XmlHighlighterColors.XML_TAG),
    new AttributesDescriptor(XmlBundle.message("options.xml.attribute.descriptor.tag.name"), XmlHighlighterColors.XML_TAG_NAME),
    new AttributesDescriptor(XmlBundle.message("options.xml.attribute.descriptor.tag.name.custom"), XmlHighlighterColors.XML_CUSTOM_TAG_NAME),
    new AttributesDescriptor(XmlBundle.message("options.xml.attribute.descriptor.matched.tag.name"), XmlHighlighterColors.MATCHED_TAG_NAME),
    new AttributesDescriptor(XmlBundle.message("options.xml.attribute.descriptor.namespace.prefix"), XmlHighlighterColors.XML_NS_PREFIX),
    new AttributesDescriptor(XmlBundle.message("options.xml.attribute.descriptor.attribute.name"), XmlHighlighterColors.XML_ATTRIBUTE_NAME),
    new AttributesDescriptor(XmlBundle.message("options.xml.attribute.descriptor.attribute.value"), XmlHighlighterColors.XML_ATTRIBUTE_VALUE),
    new AttributesDescriptor(XmlBundle.message("options.xml.attribute.descriptor.tag.data"), XmlHighlighterColors.XML_TAG_DATA),
    new AttributesDescriptor(XmlBundle.message("options.xml.attribute.descriptor.descriptor.entity,reference"), XmlHighlighterColors.XML_ENTITY_REFERENCE),
    new AttributesDescriptor(OptionsBundle.message("options.any.color.descriptor.injected.language.fragment"), XmlHighlighterColors.XML_INJECTED_LANGUAGE_FRAGMENT),
  };

  @Override
  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("options.xml.display.name");
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
  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new XmlFileHighlighter();
  }

  @Override
  @NotNull
  public String getDemoText() {
    return "<?xml version='1.0' encoding='ISO-8859-1'  ?>\n" +
           "<!DOCTYPE index>\n" +
           "<!-- Some xml example -->\n" +
           "<index version=\"1.0\" xmlns:<bg><np>pf</np></bg>=\"http://test\">\n" +
           "   <name>Main Index</name>\n" +
           "   <indexitem text=\"rename\" target=\"refactoring.rename\"/>\n" +
           "   <indexitem text=\"move\" target=\"refactoring.move\"/>\n" +
           "   <indexitem text=\"migrate\" target=\"refactoring.migrate\"/>\n" +
           "   <indexitem text=\"usage search\" target=\"find.findUsages\"/>\n" +
           "   <<matched>indexitem</matched>>Matched tag name</<matched>indexitem</matched>>\n" +
           "   <someTextWithEntityRefs>&amp; &#x00B7;</someTextWithEntityRefs>\n" +
           "   <withCData><![CDATA[\n" +
           "          <object class=\"MyClass\" key=\"constant\">\n" +
           "          </object>\n" +
           "        ]]>\n" +
           "   </withCData>\n" +
           "   <indexitem text=\"project\" target=\"project.management\"/>\n" +
           "   <<custom_tag_name>custom-tag</custom_tag_name>>" +
           "hello" +
           "</<custom_tag_name>custom_tag</custom_tag_name>>\n" +
           "   <<bg><np>pf</np></bg>:foo <bg><np>pf</np></bg>:bar=\"bar\"/>\n" +
           "</index>";
  }

  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return new ContainerUtil.ImmutableMapBuilder<String, TextAttributesKey>()
      .put("custom_tag_name", XmlHighlighterColors.XML_CUSTOM_TAG_NAME)
      .put("np", XmlHighlighterColors.XML_NS_PREFIX)
      .put("bg", XmlHighlighterColors.XML_TAG)
      .put("matched", XmlHighlighterColors.MATCHED_TAG_NAME)
      .build();
  }
}
