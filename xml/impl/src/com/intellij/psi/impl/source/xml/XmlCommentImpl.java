package com.intellij.psi.impl.source.xml;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.meta.PsiMetaBaseOwner;
import com.intellij.psi.meta.PsiMetaDataBase;
import com.intellij.psi.jsp.JspSpiUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.lang.StdLanguages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mike
 */
public class XmlCommentImpl extends XmlElementImpl implements XmlComment, XmlElementType, PsiMetaBaseOwner {
  public XmlCommentImpl() {
    super(XML_COMMENT);
  }

  public IElementType getTokenType() {
    return XML_COMMENT;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitXmlComment(this);
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
  public PsiMetaDataBase getMetaData() {
    return MetaRegistry.getMetaBase(this);
  }
}
