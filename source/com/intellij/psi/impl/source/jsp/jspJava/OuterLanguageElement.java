package com.intellij.psi.impl.source.jsp.jspJava;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.jsp.JspDirectiveKind;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CharTable;

import java.util.HashSet;
import java.util.Set;

public class OuterLanguageElement extends LeafPsiElement {
  private XmlText myFollowingText;
  private XmlTag[] myIncludes = null;

  public OuterLanguageElement(IElementType type, char[] buffer, int start, int end, CharTable table) {
    super(type, buffer, start, end, -1, table);
    myFollowingText = null;
  }

  public OuterLanguageElement(char[] buffer, int start, int end, CharTable table) {
    super(JspElementType.HOLDER_TEMPLATE_DATA, buffer, start, end, -1, table);
    myFollowingText = null;
  }

  public String toString() {
    return "JspText";
  }

  public void setFollowingText(final XmlText followingText) {
    myFollowingText = followingText;
    clearCaches();
  }

  public XmlText getFollowingText() {
    return myFollowingText;
  }

  public void clearCaches() {
    super.clearCaches();
    myIncludes = null;
  }

  public XmlTag[] getIncludeDirectivesInScope() {
    if(myIncludes != null) return myIncludes;
    final TextRange textRange = getTextRange();
    final FileViewProvider viewProvider = getContainingFile().getViewProvider();
    final JspFile jspFile = PsiUtil.getJspFile(viewProvider.getPsi(viewProvider.getBaseLanguage()));
    final XmlTag[] directiveTags = jspFile.getDirectiveTags(JspDirectiveKind.INCLUDE, false);
    final Set<XmlTag> includeDirectives = new HashSet<XmlTag>();
    for (final XmlTag directiveTag : directiveTags) {
      if (directiveTag.getNode().getStartOffset() < textRange.getStartOffset()) continue;
      if (directiveTag.getNode().getStartOffset() >= textRange.getEndOffset()) break;
      includeDirectives.add(directiveTag);
    }
    return myIncludes = includeDirectives.toArray(XmlTag.EMPTY);
  }
}
