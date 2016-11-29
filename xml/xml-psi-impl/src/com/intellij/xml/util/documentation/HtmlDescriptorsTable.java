/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.xml.util.documentation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.HashSet;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.util.*;

/**
 * @author maxim
 */
public class HtmlDescriptorsTable {
  public static final Logger LOG = Logger.getInstance(HtmlDescriptorsTable.class);

  private static final HashMap<String,HtmlTagDescriptor> ourTagTable = new HashMap<>();
  private static final HashMap<String,HtmlAttributeDescriptor> ourAttributeTable = new HashMap<>();

  @NonNls
  public static final String HTMLTABLE_RESOURCE_NAME = "htmltable.xml";

  @NonNls
  public static final String HTML5TABLE_RESOURCE_NAME = "html5table.xml";

  @NonNls
  private static final String MATHML_RESOURCE_NAME = "mathmltable.xml";

  @NonNls
  private static final String SVG_RESOURCE_NAME = "svgtable.xml";

  @NonNls
  public static final String TAG_ELEMENT_NAME = "tag";

  @NonNls
  public static final String BASE_HELP_REF_ATTR = "baseHelpRef";

  @NonNls
  public static final String NAME_ATTR = "name";

  @NonNls
  public static final String HELPREF_ATTR = "helpref";

  @NonNls
  public static final String DESCRIPTION_ATTR = "description";

  @NonNls public static final String STARTTAG_ATTR = "startTag";

  @NonNls public static final String ENDTAG_ATTR = "endTag";

  @NonNls public static final String EMPTY_ATTR = "empty";

  @NonNls public static final String DTD_ATTR = "dtd";

  @NonNls public static final String ATTRIBUTE_ELEMENT_NAME = "attribute";

  @NonNls public static final String TYPE_ATTR = "type";

  @NonNls public static final String DEFAULT_ATTR = "default";

  @NonNls public static final String RELATED_TAGS_ATTR = "relatedTags";

  private HtmlDescriptorsTable() {
  }

  static {
    try {
      Set<String> htmlTagNames = new HashSet<>();

      loadHtmlElements(HTMLTABLE_RESOURCE_NAME, htmlTagNames);

      loadHtmlElements(HTML5TABLE_RESOURCE_NAME, htmlTagNames);

      loadHtmlElements(MATHML_RESOURCE_NAME, htmlTagNames);

      loadHtmlElements(SVG_RESOURCE_NAME, htmlTagNames);

    } catch (Exception ex) {
      LOG.error(ex);
    }
  }

  private static void loadHtmlElements(final String resourceName, Collection<String> htmlTagNames) throws JDOMException, IOException {
    final Document document = JDOMUtil.loadDocument(HtmlDescriptorsTable.class.getResourceAsStream(resourceName));
    final List elements = document.getRootElement().getChildren(TAG_ELEMENT_NAME);
    final String baseHtmlExtDocUrl = document.getRootElement().getAttribute(BASE_HELP_REF_ATTR).getValue();

    for (Object object : elements) {
      final Element element = (Element)object;
      String htmlTagName = element.getAttributeValue(NAME_ATTR);
      htmlTagNames.add(htmlTagName);

      HtmlTagDescriptor value = new HtmlTagDescriptor();
      ourTagTable.put(htmlTagName, value);
      value.setHelpRef(baseHtmlExtDocUrl + element.getAttributeValue(HELPREF_ATTR));
      value.setDescription(element.getAttributeValue(DESCRIPTION_ATTR));
      value.setName(htmlTagName);

      value.setHasStartTag(element.getAttribute(STARTTAG_ATTR).getBooleanValue());
      value.setHasEndTag(element.getAttribute(ENDTAG_ATTR).getBooleanValue());
      value.setEmpty(element.getAttribute(EMPTY_ATTR).getBooleanValue());

      String attributeValue = element.getAttributeValue(DTD_ATTR);
      if (attributeValue.length() > 0) {
        value.setDtd(attributeValue.charAt(0));
      }
    }

    final List attributes = document.getRootElement().getChildren(ATTRIBUTE_ELEMENT_NAME);
    for (Object attribute : attributes) {
      final Element element = (Element)attribute;
      String attrName = element.getAttributeValue(NAME_ATTR);

      HtmlAttributeDescriptor value = new HtmlAttributeDescriptor();
      HtmlAttributeDescriptor previousDescriptor = ourAttributeTable.get(attrName);

      if (previousDescriptor == null) {
        ourAttributeTable.put(attrName, value);
      }
      else {
        CompositeAttributeTagDescriptor parentDescriptor;

        if (!(previousDescriptor instanceof CompositeAttributeTagDescriptor)) {
          parentDescriptor = new CompositeAttributeTagDescriptor();
          ourAttributeTable.put(attrName, parentDescriptor);
          parentDescriptor.attributes.add(previousDescriptor);
        }
        else {
          parentDescriptor = (CompositeAttributeTagDescriptor)previousDescriptor;
        }

        parentDescriptor.attributes.add(value);
      }

      value.setHelpRef(element.getAttributeValue(HELPREF_ATTR));
      value.setDescription(element.getAttributeValue(DESCRIPTION_ATTR));
      value.setName(attrName);

      String attributeValue = element.getAttributeValue(DTD_ATTR);
      if (attributeValue.length() > 0) {
        value.setDtd(attributeValue.charAt(0));
      }

      value.setType(element.getAttributeValue(TYPE_ATTR));
      value.setHasDefaultValue(element.getAttribute(DEFAULT_ATTR).getBooleanValue());

      StringTokenizer tokenizer = new StringTokenizer(element.getAttributeValue(RELATED_TAGS_ATTR), ",");
      int tokenCount = tokenizer.countTokens();

      for (int i = 0; i < tokenCount; ++i) {
        final String s = tokenizer.nextToken();

        if (s.equals("!")) {
          value.setParentSetIsExclusionSet(true);
        }
        else {
          if (value.getSetOfParentTags() == null) {
            value.setSetOfParentTags(new String[tokenCount - (value.isParentSetIsExclusionSet() ? 1 : 0)]);
          }
          value.getSetOfParentTags()[i - (value.isParentSetIsExclusionSet() ? 1 : 0)] = s;
        }
      }

      Arrays.sort(value.getSetOfParentTags());
    }
  }

  public static HtmlTagDescriptor getTagDescriptor(String tagName) {
    return ourTagTable.get(tagName);
  }

  public static HtmlAttributeDescriptor getAttributeDescriptor(String attributeName) {
    return ourAttributeTable.get(attributeName);
  }
}
