package com.intellij.uiDesigner;

import com.intellij.xml.util.XmlUtil;
import org.jdom.Attribute;
import org.jdom.Element;

import java.awt.*;
import java.util.Iterator;
import java.util.Stack;

/**
 * This is utility for serialization of component hierarchy.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class XmlWriter{
  private final int INDENT = 2;

  private final Stack myElementNames;
  private final Stack myElementHasBody;
  private final StringBuffer myBuffer;

  public XmlWriter(){
    myElementNames = new Stack();
    myElementHasBody = new Stack();
    myBuffer = new StringBuffer();
    myBuffer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
  }

  public String getText(){
    return myBuffer.toString();
  }

  public void writeDimension(final Dimension dimension, final String elementName) {
    if (dimension.width == -1 && dimension.height == -1) {
      return;
    }
    startElement(elementName);
    try {
      addAttribute("width", dimension.width);
      addAttribute("height", dimension.height);
    }
    finally {
      endElement();
    }
  }

  public void startElement(final String elementName){
    startElement(elementName, null);
  }

  public void startElement(final String elementName, final String namespace){
    if (myElementNames.size() > 0) {
      if(!((Boolean)myElementHasBody.peek()).booleanValue()){
        myBuffer.append(">\n");
      }
      myElementHasBody.set(myElementHasBody.size()-1,Boolean.TRUE);
    }

    writeSpaces(myElementNames.size()*INDENT);
    myBuffer.append("<").append(elementName);

    if (namespace != null) {
      myBuffer.append(" xmlns=\"").append(namespace).append('"');
    }

    myElementNames.push(elementName);
    myElementHasBody.push(Boolean.FALSE);
  }

  public void endElement() {
    final String elementName = (String)myElementNames.peek();
    final boolean hasBody = ((Boolean)myElementHasBody.peek()).booleanValue();

    myElementNames.pop();
    myElementHasBody.pop();

    if (hasBody) {
      writeSpaces(myElementNames.size()*INDENT);
      myBuffer.append("</").append(elementName).append(">\n");
    } else {
      myBuffer.append("/>\n");
    }
  }

  /**
   * Helper method
   */
  private void addAttributeImpl(final String name,final String value){
    myBuffer.append(' ').append(name).append("=\"").append(value).append('"');
  }

  /**
   * Helper method
   */
  public void addAttribute(final String name, final String value){
    addAttributeImpl(name, XmlUtil.escapeString(value));
  }

  /**
   * Helper method
   */
  public void addAttribute(final String name, final int value){
    addAttributeImpl(name, Integer.toString(value));
  }

  /**
   * Helper method
   */
  public void addAttribute(final String name, final boolean value){
    addAttributeImpl(name, Boolean.toString(value));
  }


  public void writeElement(final Element element){
    startElement(element.getName());
    try {
      for (Iterator iterator = element.getAttributes().iterator(); iterator.hasNext();) {
        final Attribute attribute = (Attribute)iterator.next();
        addAttribute(attribute.getName(), attribute.getValue());
      }
      for (Iterator iterator = element.getChildren().iterator(); iterator.hasNext(); ){
        final Element child = (Element)iterator.next();
        writeElement(child);
      }
    }
    finally {
      endElement();
    }
  }

  /**
   * Helper method
   */
  private void writeSpaces(final int count){
    for (int i=0; i < count; i++) {
      myBuffer.append(' ');
    }
  }

}
