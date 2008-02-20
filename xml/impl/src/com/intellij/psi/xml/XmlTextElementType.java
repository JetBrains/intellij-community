/*
 * @author max
 */
package com.intellij.psi.xml;

import com.intellij.psi.tree.IStrongWhitespaceHolderElementType;
import com.intellij.psi.tree.xml.IXmlElementType;

class XmlTextElementType extends IXmlElementType implements IStrongWhitespaceHolderElementType {
  public XmlTextElementType() {
    super("XML_TEXT");
  }
}