package com.intellij.tools;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizable;
import org.jdom.Element;

import java.util.Iterator;

/**
 * @author dyoma
 */
class FilterInfo implements JDOMExternalizable {
  private static final String FILTER_NAME="NAME";
  private static final String FILTER_DESCRIPTION="DESCRIPTION";
  private static final String FILTER_REGEXP="REGEXP";

  private String myName = "No name";
  private String myDescription;
  private String myRegExp;

  public FilterInfo() {}

  public FilterInfo(String regExp, String name, String description) {
    myRegExp = regExp;
    myName = name;
    myDescription = description;
  }

  public String getDescription() {
    return myDescription;
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  public String getRegExp() {
    return myRegExp;
  }

  public void setRegExp(String regExp) {
    myRegExp = regExp;
  }

  public int hashCode() {
    return Comparing.hashcode(myName) +
           Comparing.hashcode(myDescription) +
           Comparing.hashcode(myRegExp);
  }

  public boolean equals(Object object) {
    if (!(object instanceof FilterInfo)) return false;
    FilterInfo other = (FilterInfo)object;
    return Comparing.equal(myName, other.myName) &&
           Comparing.equal(myDescription, other.myDescription) &&
           Comparing.equal(myRegExp, other.myRegExp);
  }

  public FilterInfo createCopy() {
    return new FilterInfo(myRegExp, myName, myDescription);
  }

  public void readExternal(Element element) {
    for (Iterator i2 = element.getChildren("option").iterator(); i2.hasNext(); ) {
      Element optionElement = (Element)i2.next();
      String value = optionElement.getAttributeValue("value");
      String name = optionElement.getAttributeValue("name");

      if (FILTER_NAME.equals(name)) {
        if (value != null) {
          myName = convertString(value);
        }
      }
      if (FILTER_DESCRIPTION.equals(name)) {
        myDescription = convertString(value);
      }
      if (FILTER_REGEXP.equals(name)) {
        myRegExp = convertString(value);
      }
    }
  }

  public void writeExternal(Element filterElement) {
    Element option = new Element("option");
    filterElement.addContent(option);
    option.setAttribute("name", FILTER_NAME);
    if (myName != null ) {
      option.setAttribute("value", myName);
    }

    option = new Element("option");
    filterElement.addContent(option);
    option.setAttribute("name", FILTER_DESCRIPTION);
    if (myDescription != null ) {
      option.setAttribute("value", myDescription);
    }

    option = new Element("option");
    filterElement.addContent(option);
    option.setAttribute("name", FILTER_REGEXP);
    if (myRegExp != null ) {
      option.setAttribute("value", myRegExp);
    }
  }

  public static String convertString(String s) {
    return ToolSettings.convertString(s);
  }
}
