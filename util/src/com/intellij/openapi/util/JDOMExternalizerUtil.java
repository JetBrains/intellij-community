package com.intellij.openapi.util;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.diagnostic.Logger;
import org.jdom.Element;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 13, 2004
 * Time: 2:58:50 PM
 * To change this template use File | Settings | File Templates.
 */
public class JDOMExternalizerUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.JDOMExternalizerUtil");
    
  public static void writeField(Element root, String fieldName, String value) {
    Element element = new Element("option");
    element.setAttribute("name", fieldName);
    element.setAttribute("value", value == null ? "" : value);
    root.addContent(element);
  }

  public static String readField(Element parent, String fieldName) {
    List list = parent.getChildren("option");
    for (int i = 0; i < list.size(); i++) {
      Element element = (Element)list.get(i);
      String childName = element.getAttributeValue("name");
      if (Comparing.strEqual(childName, fieldName)) {
        return element.getAttributeValue("value");
      }
    }
    return null;
  }
}
