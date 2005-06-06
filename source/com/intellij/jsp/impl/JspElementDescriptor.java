package com.intellij.jsp.impl;

import com.intellij.xml.XmlElementDescriptor;
import com.intellij.j2ee.j2eeDom.web.WebModuleProperties;
import com.intellij.psi.xml.XmlTag;

import javax.servlet.jsp.tagext.TagExtraInfo;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 11, 2005
 * Time: 8:57:52 PM
 * To change this template use File | Settings | File Templates.
 */
public interface JspElementDescriptor extends XmlElementDescriptor {
  TagExtraInfo getExtraInfo(WebModuleProperties moduleProperties);
  boolean isRequiredAttributeImplicitlyPresent(XmlTag tag,String attributeName);
}
