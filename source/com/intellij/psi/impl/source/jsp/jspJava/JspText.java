package com.intellij.psi.impl.source.jsp.jspJava;

import com.intellij.psi.impl.source.tree.JspElementType;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.CharTable;

public class JspText extends LeafPsiElement {
  private final XmlText myFollowingText;

  public JspText(XmlText text, char[] buffer, int start, int end, CharTable table) {
    super(JspElementType.HOLDER_TEMPLATE_DATA, buffer, start, end, -1, table);
    myFollowingText = text;
  }

  public String toString() {
    return "JspText";
  }

  public void setText(String text) {
    super.setText(text);
  }

  public XmlText getFollowingText() {
    return myFollowingText;
  }
}
