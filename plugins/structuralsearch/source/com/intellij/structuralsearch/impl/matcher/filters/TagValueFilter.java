package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;

/**
 * Created by IntelliJ IDEA.
 * User: maxim.mossienko
 * Date: Oct 12, 2005
 * Time: 4:44:19 PM
 * To change this template use File | Settings | File Templates.
 */
public class TagValueFilter extends XmlElementVisitor implements NodeFilter {
  private boolean result;

  @Override public void visitXmlText(XmlText text) {
    result = true;
  }

  @Override public void visitXmlTag(XmlTag tag) {
    result = true;
  }

  private static class NodeFilterHolder {
    private static final NodeFilter instance = new TagValueFilter();
  }

  public static NodeFilter getInstance() {
    return NodeFilterHolder.instance;
  }

  private TagValueFilter() {
  }

  public boolean accepts(PsiElement element) {
    result = false;
    if (element!=null) element.accept(this);
    return result;
  }
}
