package com.intellij.util.xml.impl;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementPresentation;

import javax.swing.*;

/**
 * User: Sergey.Vasiliev
 */
public class BasicDomElementPresentation implements DomElementPresentation {
  private DomElement myElement;


  public BasicDomElementPresentation(final DomElement element) {
    myElement = element;
  }


  public DomElement getElement() {
    return myElement;
  }

  public String getElementName() {
    final String name = myElement.getGenericInfo().getElementName(myElement);
    return name == null ? getTypeName() : name;
  }

  public String getTypeName() {
    return StringUtil.capitalizeWords(getElement().getNameStrategy().splitIntoWords(getElement().getXmlElementName()), true);
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/fileTypes/unknown.png");
  }
}
