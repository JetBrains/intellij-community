// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.analysis.CreateNSDeclarationIntentionFix;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.psi.XmlPsiBundle;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author maxim.mossienko
 */
public class AddXsiSchemaLocationForExtResourceAction extends BaseExtResourceAction {
  private static final @NonNls String XMLNS_XSI_ATTR_NAME = "xmlns:xsi";
  private static final @NonNls String XSI_SCHEMA_LOCATION_ATTR_NAME = "xsi:schemaLocation";
  private static final String KEY = "xml.intention.add.xsi.schema.location.for.external.resource";

  @Override
  protected String getQuickFixKeyId() {
    return KEY;
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return currentFile;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected void doInvoke(final @NotNull PsiFile psiFile, final int offset, final @NotNull String uri, final Editor editor) throws IncorrectOperationException {
    final XmlTag tag = PsiTreeUtil.getParentOfType(psiFile.findElementAt(offset), XmlTag.class);
    if (tag == null) return;
    final List<String> schemaLocations = new ArrayList<>();

    CreateNSDeclarationIntentionFix.processExternalUris(new CreateNSDeclarationIntentionFix.TagMetaHandler(tag.getLocalName()), psiFile, new CreateNSDeclarationIntentionFix.ExternalUriProcessor() {
      @Override
      public void process(final @NotNull String currentUri, final String url) {
        if (currentUri.equals(uri) && url != null) schemaLocations.add(url);
      }
    });

    CreateNSDeclarationIntentionFix.runActionOverSeveralAttributeValuesAfterLettingUserSelectTheNeededOne(
      ArrayUtilRt.toStringArray(schemaLocations), psiFile.getProject(), new CreateNSDeclarationIntentionFix.StringToAttributeProcessor() {
        @Override
        public void doSomethingWithGivenStringToProduceXmlAttributeNowPlease(final @NotNull String attrName) throws IncorrectOperationException {
          doIt(psiFile, editor, uri, tag, attrName);
        }
      }, XmlPsiBundle.message("xml.action.select.namespace.location.title"), this, editor);
  }

  private static void doIt(final PsiFile psiFile, final Editor editor, final String uri, final XmlTag tag, final String s) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(psiFile)) return;
    final XmlElementFactory elementFactory = XmlElementFactory.getInstance(psiFile.getProject());

    if (tag.getAttributeValue(XMLNS_XSI_ATTR_NAME) == null) {
      tag.add(elementFactory.createXmlAttribute(XMLNS_XSI_ATTR_NAME, XmlUtil.XML_SCHEMA_INSTANCE_URI));
    }

    final XmlAttribute locationAttribute = tag.getAttribute(XSI_SCHEMA_LOCATION_ATTR_NAME);
    final String toInsert = uri + " " + s;
    int offset = s.length();

    if (locationAttribute == null) {
      tag.add(elementFactory.createXmlAttribute(XSI_SCHEMA_LOCATION_ATTR_NAME, toInsert));
    } else {
      final String newValue = locationAttribute.getValue() + "\n" + toInsert;
      locationAttribute.setValue(newValue);
    }

    PsiDocumentManager.getInstance(psiFile.getProject()).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    CodeStyleManager.getInstance(psiFile.getProject()).reformat(tag);

    @SuppressWarnings("ConstantConditions")
    final TextRange range = tag.getAttribute(XSI_SCHEMA_LOCATION_ATTR_NAME).getValueElement().getTextRange();
    final TextRange textRange = new TextRange(range.getEndOffset() - offset - 1, range.getEndOffset() - 1);
    editor.getCaretModel().moveToOffset(textRange.getStartOffset());
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (!(psiFile instanceof XmlFile)) return false;

    PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
    XmlAttributeValue value = PsiTreeUtil.getParentOfType(element, XmlAttributeValue.class);
    if (value == null) return false;
    XmlAttribute attribute = PsiTreeUtil.getParentOfType(value, XmlAttribute.class);
    if (attribute != null && attribute.isNamespaceDeclaration()) {
      setText(XmlBundle.message(getQuickFixKeyId()));
      return true;
    }
    return false;
  }
}