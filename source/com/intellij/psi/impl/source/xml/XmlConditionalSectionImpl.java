package com.intellij.psi.impl.source.xml;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlConditionalSection;
import com.intellij.psi.PsiElementVisitor;

/**
 * Created by IntelliJ IDEA.
 * User: maxim.mossienko
 * Date: Jan 13, 2006
 * Time: 6:55:25 PM
 * To change this template use File | Settings | File Templates.
 */
public class XmlConditionalSectionImpl extends XmlElementImpl implements XmlConditionalSection {
  public XmlConditionalSectionImpl() {
    super(XML_CONDITIONAL_SECTION);
  }
}
