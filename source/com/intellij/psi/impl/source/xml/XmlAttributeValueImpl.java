package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.ReplaceableTextPsiElement;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.impl.ant.AntPropertyDeclaration;

/**
 * @author Mike
 */
public class XmlAttributeValueImpl extends XmlElementImpl implements XmlAttributeValue, ReplaceableTextPsiElement{
  public XmlAttributeValueImpl() {
    super(XML_ATTRIBUTE_VALUE);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitXmlAttributeValue(this);
  }

  public String getValue() {
    String text = getText();
    if (StringUtil.startsWithChar(text, '\"') || StringUtil.startsWithChar(text, '\'')) text = text.substring(1);
    if (StringUtil.endsWithChar(text, '\"') || StringUtil.endsWithChar(text, '\'')) text = text.substring(0, text.length() - 1);

    return text;
  }

  public PsiReference[] getReferences() {
    return ResolveUtil.getReferencesFromProviders(this);
  }

  public PsiElement replaceRangeInText(final TextRange range, String newSubText)
    throws IncorrectOperationException {
    XmlFile file = (XmlFile) getManager().getElementFactory().createFileFromText("dummy.xml", "<a attr=" + getNewText(range, newSubText) + "/>");
    return XmlAttributeValueImpl.this.replace(file.getDocument().getRootTag().getAttributes()[0].getValueElement());
  }

  private String getNewText(final TextRange range, String newSubstring) {
    final String text = XmlAttributeValueImpl.this.getText();
    return text.substring(0, range.getStartOffset()) + newSubstring + text.substring(range.getEndOffset());
  }

  public int getTextOffset() {
    return getTextRange().getStartOffset() + 1;
  }
}
