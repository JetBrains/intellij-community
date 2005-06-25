package com.intellij.psi.impl.source.jsp.jspJava;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.jsp.JspDirectiveKind;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.CharTable;

import java.util.HashSet;
import java.util.Set;

public class JspText extends LeafPsiElement {
  private XmlText myFollowingText;
  private Set<XmlTag> myIncludes = null;

  public JspText(IElementType type, char[] buffer, int start, int end, CharTable table) {
    super(type, buffer, start, end, -1, table);
    myFollowingText = null;
  }

  public JspText(char[] buffer, int start, int end, CharTable table) {
    super(JspElementType.HOLDER_TEMPLATE_DATA, buffer, start, end, -1, table);
    myFollowingText = null;
  }

  public String toString() {
    return "JspText";
  }

  public void setFollowingText(final XmlText followingText) {
    myFollowingText = followingText;
  }

  public XmlText getFollowingText() {
    return myFollowingText;
  }

  public void clearCaches() {
    super.clearCaches();
    myIncludes = null;
  }

  public Set<XmlTag> getIncludeDirectivesInScope() {
    if(myIncludes != null) return myIncludes;
    final TextRange textRange = getTextRange();
    final JspFile jspFile = (JspFile)getContainingFile();
    final XmlTag[] directiveTags = jspFile.getDirectiveTags(JspDirectiveKind.INCLUDE, false);
    final Set<XmlTag> includeDirectives = new HashSet<XmlTag>();
    for (int i = 0; i < directiveTags.length; i++) {
      final XmlTag directiveTag = directiveTags[i];
      if(directiveTag.getNode().getStartOffset() < textRange.getStartOffset()) continue;
      if(directiveTag.getNode().getStartOffset() >= textRange.getEndOffset()) break;
      includeDirectives.add(directiveTag);
    }
    return myIncludes = includeDirectives;
  }
}
