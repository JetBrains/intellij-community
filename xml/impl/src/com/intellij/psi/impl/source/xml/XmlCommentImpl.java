package com.intellij.psi.impl.source.xml;

import com.intellij.lang.StdLanguages;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.jsp.JspSpiUtil;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mike
 */
public class XmlCommentImpl extends XmlElementImpl implements XmlComment, XmlElementType, PsiMetaOwner {
  public XmlCommentImpl() {
    super(XML_COMMENT);
  }

  public IElementType getTokenType() {
    return XML_COMMENT;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlComment(this);
    }
    else {
      visitor.visitComment(this);
    }
  }

  public XmlTag getParentTag() {
    if(getParent() instanceof XmlTag) return (XmlTag)getParent();
    return null;
  }

  public XmlTagChild getNextSiblingInTag() {
    if(getParent() instanceof XmlTag) return (XmlTagChild)getNextSibling();
    return null;
  }

  public XmlTagChild getPrevSiblingInTag() {
    if(getParent() instanceof XmlTag) return (XmlTagChild)getPrevSibling();
    return null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    if (getParent().getLanguage() == StdLanguages.JSPX) {
      return JspSpiUtil.getReferencesForXmlCommentInJspx(this);
    }
    return super.getReferences();
  }

  @Nullable
  public PsiMetaData getMetaData() {
    return MetaRegistry.getMetaBase(this);
  }
}
