// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath.xslt.validation;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.pages.XMLColorsPage;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.plugins.xpathView.XPathBundle;
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
    return XPathBundle.message("configurable.xslt.display.name");
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return new AttributesDescriptor[] {
      new AttributesDescriptor(XPathBundle.message("attribute.descriptor.xslt.directive"), XsltSupport.XSLT_DIRECTIVE)
    };
  }

  @NotNull
  @Override
  public String getDemoText() {
    return """
      <xsl><xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"></xsl>
          <xsl><xsl:template match="/hello-world"></xsl>
              <html>
                  <body>
                      <xsl><xsl:value-of select="greeting"/></xsl>
                  </body>
              </html>
          <xsl></xsl:template></xsl>
      <xsl></xsl:stylesheet></xsl>""";
  }

  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return Collections.singletonMap("xsl", XsltSupport.XSLT_DIRECTIVE);
  }
}
