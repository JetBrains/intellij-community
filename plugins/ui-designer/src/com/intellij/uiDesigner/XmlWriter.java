// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.lw.ColorDescriptor;
import com.intellij.uiDesigner.lw.FontDescriptor;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.util.containers.BooleanStack;
import com.intellij.util.containers.Stack;
import com.intellij.xml.util.XmlStringUtil;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.awt.*;

/**
 * This is utility for serialization of component hierarchy.
 */
public final class XmlWriter{
  private static final int INDENT = 2;

  private final Stack<String> myElementNames = new Stack<>();
  private final BooleanStack myElementHasBody = new BooleanStack();
  private final @NonNls StringBuffer myBuffer = new StringBuffer("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

  public String getText(){
    return myBuffer.toString();
  }

  public void writeDimension(final Dimension dimension, final @NonNls String elementName) {
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

  public void startElement(final @NonNls String elementName){
    startElement(elementName, null);
  }

  public void startElement(final @NonNls String elementName, final String namespace){
    if (!myElementNames.isEmpty()) {
      if(!myElementHasBody.peek()){
        myBuffer.append(">\n");
      }
      myElementHasBody.pop();
      myElementHasBody.push(true);
    }

    writeSpaces(myElementNames.size()*INDENT);
    myBuffer.append("<").append(elementName);

    if (namespace != null) {
      myBuffer.append(" xmlns=\"").append(namespace).append('"');
    }

    myElementNames.push(elementName);
    myElementHasBody.push(false);
  }

  public void endElement() {
    final String elementName = myElementNames.peek();
    final boolean hasBody = myElementHasBody.peek();

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
  public void addAttribute(final @NonNls String name, final String value){
    addAttributeImpl(name, StringUtil.convertLineSeparators(XmlStringUtil.escapeString(value, true, false)));
  }

  /**
   * Helper method
   */
  public void addAttribute(final @NonNls String name, final int value){
    addAttributeImpl(name, Integer.toString(value));
  }

  /**
   * Helper method
   */
  public void addAttribute(final @NonNls String name, final boolean value){
    addAttributeImpl(name, Boolean.toString(value));
  }

  public void addAttribute(final @NonNls String name, final Double value){
    addAttributeImpl(name, Double.toString(value));
  }

  public void writeElement(final Element element){
    startElement(element.getName());
    try {
      for (final Attribute attribute : element.getAttributes()) {
        addAttribute(attribute.getName(), attribute.getValue());
      }
      for (final Element child : element.getChildren()) {
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
    myBuffer.append(" ".repeat(count));
  }

  public void writeStringDescriptor(final StringDescriptor descriptor,
                                    final String valueAttr,
                                    final String bundleAttr,
                                    final String keyAttr) {
    if(descriptor.getValue() != null){ // direct value
      addAttribute(valueAttr, descriptor.getValue());
      if (descriptor.isNoI18n()) {
        addAttribute(UIFormXmlConstants.ATTRIBUTE_NOI18N, Boolean.TRUE.toString());
      }
    }
    else{ // via resource bundle
      addAttribute(bundleAttr, descriptor.getBundleName());
      addAttribute(keyAttr, descriptor.getKey());
    }
  }

  public void writeColorDescriptor(final ColorDescriptor value) {
    Color color = value.getColor();
    if (color != null) {
      addAttribute(UIFormXmlConstants.ATTRIBUTE_COLOR, color.getRGB());
    }
    else if (value.getSwingColor() != null) {
      addAttribute(UIFormXmlConstants.ATTRIBUTE_SWING_COLOR, value.getSwingColor());
    }
    else if (value.getSystemColor() != null) {
      addAttribute(UIFormXmlConstants.ATTRIBUTE_SYSTEM_COLOR, value.getSystemColor());
    }
    else if (value.getAWTColor() != null) {
      addAttribute(UIFormXmlConstants.ATTRIBUTE_AWT_COLOR, value.getAWTColor());
    }
  }

  public void writeFontDescriptor(final FontDescriptor value) {
    if (value.getSwingFont() != null) {
      addAttribute(UIFormXmlConstants.ATTRIBUTE_SWING_FONT, value.getSwingFont());
    }
    else {
      if (value.getFontName() != null) {
        addAttribute(UIFormXmlConstants.ATTRIBUTE_NAME, value.getFontName());
      }
      if (value.getFontSize() >= 0) {
        addAttribute(UIFormXmlConstants.ATTRIBUTE_SIZE, value.getFontSize());
      }
      if (value.getFontStyle() >= 0) {
        addAttribute(UIFormXmlConstants.ATTRIBUTE_STYLE, value.getFontStyle());
      }
    }
  }

  public void writeInsets(final Insets value) {
    addAttribute(UIFormXmlConstants.ATTRIBUTE_TOP, value.top);
    addAttribute(UIFormXmlConstants.ATTRIBUTE_LEFT, value.left);
    addAttribute(UIFormXmlConstants.ATTRIBUTE_BOTTOM, value.bottom);
    addAttribute(UIFormXmlConstants.ATTRIBUTE_RIGHT, value.right);
  }
}
