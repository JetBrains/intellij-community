// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml;

import com.intellij.application.options.colors.highlighting.HighlightData;
import com.intellij.application.options.colors.highlighting.HighlightsExtractor;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.options.colors.pages.XMLColorsPage;
import com.intellij.testFramework.LightPlatformTestCase;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class XmlHighlightsExtractorTest extends LightPlatformTestCase {
  private static final TextAttributesKey INLINE_KEY = TextAttributesKey.createTextAttributesKey("INLINE_KEY");
  private static final ColorKey COLOR_KEY = ColorKey.createColorKey("COLOR_KEY");

  @NonNls private static final Map<String, TextAttributesKey> INLINE_ELEMENT_DESCRIPTORS = new THashMap<>();
  static {
    INLINE_ELEMENT_DESCRIPTORS.put("inline", INLINE_KEY);
  }
  private static final Map<String, ColorKey> COLOR_KEY_MAPPING = new HashMap<>();
  static {
    COLOR_KEY_MAPPING.put("test_3", COLOR_KEY);
  }

  public void testXmlHighlights() {
    XMLColorsPage page = new XMLColorsPage();
    Map<String, TextAttributesKey> map = page.getAdditionalHighlightingTagToDescriptorMap();
    ArrayList<HighlightData> highlights = new ArrayList<>();
    String s = new HighlightsExtractor(map, INLINE_ELEMENT_DESCRIPTORS, COLOR_KEY_MAPPING).extractHighlights(page.getDemoText(),
                                                                                                             highlights);
    assertEquals(6, highlights.size());
    assertEquals("<?xml version='1.0' encoding='ISO-8859-1'  ?>\n" +
                 "<!DOCTYPE index>\n" +
                 "<!-- Some xml example -->\n" +
                 "<index version=\"1.0\" xmlns:pf=\"http://test\">\n" +
                 "   <name>Main Index</name>\n" +
                 "   <indexitem text=\"rename\" target=\"refactoring.rename\"/>\n" +
                 "   <indexitem text=\"move\" target=\"refactoring.move\"/>\n" +
                 "   <indexitem text=\"migrate\" target=\"refactoring.migrate\"/>\n" +
                 "   <indexitem text=\"usage search\" target=\"find.findUsages\"/>\n" +
                 "   <someTextWithEntityRefs>&amp; &#x00B7;</someTextWithEntityRefs>\n" +
                 "   <withCData><![CDATA[\n" +
                 "          <object class=\"MyClass\" key=\"constant\">\n" +
                 "          </object>\n" +
                 "        ]]>\n" +
                 "   </withCData>\n" +
                 "   <indexitem text=\"project\" target=\"project.management\"/>\n" +
                 "   <pf:foo pf:bar=\"bar\"/>\n" +
                 "</index>", s);
  }
}

