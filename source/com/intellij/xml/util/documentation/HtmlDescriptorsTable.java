package com.intellij.xml.util.documentation;

import org.jdom.Document;
import org.jdom.Element;

import java.util.*;

import com.intellij.openapi.util.JDOMUtil;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 24.12.2004
 * Time: 23:56:32
 * To change this template use File | Settings | File Templates.
 */
public class HtmlDescriptorsTable {
  private static final HashMap<String,HtmlTagDescriptor> ourTagTable = new HashMap<String, HtmlTagDescriptor>();
  private static final HashMap<String,HtmlAttributeDescriptor> ourAttributeTable = new HashMap<String, HtmlAttributeDescriptor>();
  private static String[] ourHtmlTagNames;

  static {
    try {
      final Document document = JDOMUtil.loadDocument(HtmlDescriptorsTable.class.getResourceAsStream("htmltable.xml"));
      final List elements = document.getRootElement().getChildren("tag");
      HtmlDocumentationProvider.setBaseHtmlExtDocUrl(
        document.getRootElement().getAttribute("baseHelpRef").getValue()
      );

      ourHtmlTagNames = new String[elements.size()];

      int i = 0;
      for (Iterator iterator = elements.iterator(); iterator.hasNext();) {
        final Element element = (Element) iterator.next();
        ourHtmlTagNames[i] = element.getAttributeValue("name");

        HtmlTagDescriptor value = new HtmlTagDescriptor();
        ourTagTable.put(ourHtmlTagNames[i],value);
        value.setHelpRef( element.getAttributeValue("helpref") );
        value.setDescription( element.getAttributeValue("description") );
        value.setName(ourHtmlTagNames[i]);

        value.setHasStartTag(element.getAttribute("startTag").getBooleanValue());
        value.setHasEndTag(element.getAttribute("endTag").getBooleanValue());
        value.setEmpty(element.getAttribute("empty").getBooleanValue());

        String attributeValue = element.getAttributeValue("dtd");
        if (attributeValue.length() > 0) {
          value.setDtd(attributeValue.charAt(0));
        }

        ++i;
      }

      final List attributes = document.getRootElement().getChildren("attribute");
      for (Iterator iterator = attributes.iterator(); iterator.hasNext();) {
        final Element element = (Element) iterator.next();
        String attrName = element.getAttributeValue("name");

        HtmlAttributeDescriptor value = new HtmlAttributeDescriptor();
        HtmlAttributeDescriptor previousDescriptor = ourAttributeTable.get(attrName);

        if (previousDescriptor==null)
          ourAttributeTable.put(attrName,value);
        else {
          CompositeAttributeTagDescriptor parentDescriptor;

          if (!(previousDescriptor instanceof CompositeAttributeTagDescriptor)) {
            parentDescriptor = new CompositeAttributeTagDescriptor();
            ourAttributeTable.put(attrName,parentDescriptor);
            parentDescriptor.attributes.add(previousDescriptor);
          } else {
            parentDescriptor = (CompositeAttributeTagDescriptor)previousDescriptor;
          }

          parentDescriptor.attributes.add(value);
        }

        value.setHelpRef( element.getAttributeValue("helpref") );
        value.setDescription( element.getAttributeValue("description") );
        value.setName(attrName);

        String attributeValue = element.getAttributeValue("dtd");
        if (attributeValue.length() > 0) {
          value.setDtd(attributeValue.charAt(0));
        }

        value.setType( element.getAttributeValue("type") );
        value.setHasDefaultValue( element.getAttribute("default").getBooleanValue() );

        StringTokenizer tokenizer = new StringTokenizer(element.getAttributeValue("relatedTags"),",");
        int tokenCount = tokenizer.countTokens();

        for(i = 0;i < tokenCount;++i) {
          final String s = tokenizer.nextToken();

          if (s.equals("!")) {
            value.setParentSetIsExclusionSet(true);
          }
          else {
            if (value.getSetOfParentTags() == null) {
              value.setSetOfParentTags(new String[tokenCount - (value.isParentSetIsExclusionSet() ? 1 : 0)]);
            }
            value.getSetOfParentTags()[i-(value.isParentSetIsExclusionSet() ? 1 : 0)] = s;
          }
        }

        Arrays.sort(value.getSetOfParentTags());
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      ourHtmlTagNames = new String[0];
    }
  }

  static HtmlTagDescriptor getTagDescriptor(String tagName) {
    return ourTagTable.get(tagName);
  }

  static HtmlAttributeDescriptor getAttributeDescriptor(String attributeName) {
    return ourAttributeTable.get(attributeName);
  }

  public static String[] getHtmlTagNames() {
    return ourHtmlTagNames;
  }
}
