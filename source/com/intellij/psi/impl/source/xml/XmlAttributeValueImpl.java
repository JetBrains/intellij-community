package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.resolve.reference.impl.manipulators.XmlAttributeValueManipulator;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mike
 */
public class XmlAttributeValueImpl extends XmlElementImpl implements XmlAttributeValue, PsiLanguageInjectionHost {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlAttributeValueImpl");
  private static final Class ourReferenceClass = XmlAttributeValue.class;

  public XmlAttributeValueImpl() {
    super(XmlElementType.XML_ATTRIBUTE_VALUE);
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

  public PsiReference getReference() {
    final PsiReference[] refs = getReferences();
    if (refs.length > 0) return refs[0];
    return null;
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
  public List<Pair<PsiElement,TextRange>> getInjectedPsi() {
    return InjectedLanguageUtil.getInjectedPsiFiles(this, InjectedLanguageUtil.XmlAttributeLiteralEscaper.INSTANCE);
  }

  public void fixText(String text) {
    try {
      String contents = StringUtil.trimEnd(StringUtil.trimStart(text, "\""), "\"");
      new XmlAttributeValueManipulator().handleContentChange(this, contents);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
