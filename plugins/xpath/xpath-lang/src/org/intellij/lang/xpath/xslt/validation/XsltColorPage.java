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
package org.intellij.lang.xpath.xslt.validation;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.pages.XMLColorsPage;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class XsltColorPage extends XMLColorsPage {
  @NotNull
  @Override
  public String getDisplayName() {
    return "XSLT";
  }

  @NotNull
  @Override
  public AttributesDescriptor[] getAttributeDescriptors() {
    return new AttributesDescriptor[] { new AttributesDescriptor("XSLT directive", XsltSupport.XSLT_DIRECTIVE)};
  }

  @NotNull
  @Override
  public String getDemoText() {
    return "<xsl><xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"1.0\"></xsl>\n" +
           "    <xsl><xsl:template match=\"/hello-world\"></xsl>\n" +
           "        <html>\n" +
           "            <body>\n" +
           "                <xsl><xsl:value-of select=\"greeting\"/></xsl>\n" +
           "            </body>\n" +
           "        </html>\n" +
           "    <xsl></xsl:template></xsl>\n" +
           "<xsl></xsl:stylesheet></xsl>";
  }

  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return Collections.singletonMap("xsl", XsltSupport.XSLT_DIRECTIVE);
  }
}
