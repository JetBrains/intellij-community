package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.tree.InjectedLanguageUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mike
 */
public class XmlAttributeValueImpl extends XmlElementImpl implements XmlAttributeValue{
  private static final Class ourReferenceClass = XmlAttributeValue.class;

  public XmlAttributeValueImpl() {
    super(XML_ATTRIBUTE_VALUE);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitXmlAttributeValue(this);
  }

  public String getValue() {
    return StringUtil.stripQuotesAroundValue(getText());
  }

  @NotNull
  public PsiReference[] getReferences() {

    return ResolveUtil.getReferencesFromProviders(this, ourReferenceClass);
  }

  public PsiElement replaceRangeInText(final TextRange range, String newSubText)
    throws IncorrectOperationException {
    XmlFile file = (XmlFile) getManager().getElementFactory().createFileFromText("dummy.xml", "<a attr=" + getNewText(range, newSubText) + "/>");
    return replace(file.getDocument().getRootTag().getAttributes()[0].getValueElement());
  }

  private String getNewText(final TextRange range, String newSubstring) {
    final String text = getText();
    return text.substring(0, range.getStartOffset()) + newSubstring + text.substring(range.getEndOffset());
  }

  public int getTextOffset() {
    return getTextRange().getStartOffset() + 1;
  }

  @Nullable
  public Pair<PsiElement, TextRange> getInjectedPsi() {
    ASTNode[] children = getChildren(null);
    TextRange insideQuotes = new TextRange(0, getTextLength());

    if (children.length > 1 && children[0].getElementType() == XML_ATTRIBUTE_VALUE_START_DELIMITER) {
      insideQuotes = new TextRange(children[1].getTextRange().getStartOffset() - getTextRange().getStartOffset(), insideQuotes.getEndOffset());
    }
    if (children.length > 1 && children[children.length-1].getElementType() == XML_ATTRIBUTE_VALUE_END_DELIMITER) {
      insideQuotes = new TextRange(insideQuotes.getStartOffset(), children[children.length-2].getTextRange().getEndOffset() - getTextRange().getStartOffset());
    }

    return InjectedLanguageUtil.createInjectedPsiFile(this, getValue(), insideQuotes);
  }
}
