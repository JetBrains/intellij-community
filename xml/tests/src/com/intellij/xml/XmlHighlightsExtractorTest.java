// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml;

import com.intellij.application.options.colors.highlighting.HighlightData;
import com.intellij.application.options.colors.highlighting.HighlightsExtractor;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.options.colors.pages.XMLColorsPage;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XmlHighlightsExtractorTest extends LightPlatformTestCase {
  private static final TextAttributesKey INLINE_KEY = TextAttributesKey.createTextAttributesKey("INLINE_KEY");
  private static final ColorKey COLOR_KEY = ColorKey.createColorKey("COLOR_KEY");

  @NonNls private static final Map<String, TextAttributesKey> INLINE_ELEMENT_DESCRIPTORS = new HashMap<>();
  static {
    INLINE_ELEMENT_DESCRIPTORS.put("inline", INLINE_KEY);
  }
  private static final Map<String, ColorKey> COLOR_KEY_MAPPING = new HashMap<>();
  static {
    COLOR_KEY_MAPPING.put("test_3", COLOR_KEY);
  }

  public void testXmlHighlights() {
    ColorSettingsPage page = new XMLColorsPage();
    Map<String, TextAttributesKey> map = page.getAdditionalHighlightingTagToDescriptorMap();
    List<HighlightData> highlights = new ArrayList<>();
    String s = new HighlightsExtractor(map, INLINE_ELEMENT_DESCRIPTORS, COLOR_KEY_MAPPING).extractHighlights(page.getDemoText(),
                                                                                                             highlights);
    assertEquals(10, highlights.size());
    assertEquals("""
                   <?xml version='1.0' encoding='ISO-8859-1'  ?>
                   <!DOCTYPE index>
                   <!-- Some xml example -->
                   <index version="1.0" xmlns:pf="http://test">
                      <name>Main Index</name>
                      <indexitem text="rename" target="refactoring.rename"/>
                      <indexitem text="move" target="refactoring.move"/>
                      <indexitem text="migrate" target="refactoring.migrate"/>
                      <indexitem text="usage search" target="find.findUsages"/>
                      <indexitem>Matched tag name</indexitem>
                      <someTextWithEntityRefs>&amp; &#x00B7;</someTextWithEntityRefs>
                      <withCData><![CDATA[
                             <object class="MyClass" key="constant">
                             </object>
                           ]]>
                      </withCData>
                      <indexitem text="project" target="project.management"/>
                      <custom-tag>hello</custom_tag>
                      <pf:foo pf:bar="bar"/>
                   </index>""", s);
  }
}

