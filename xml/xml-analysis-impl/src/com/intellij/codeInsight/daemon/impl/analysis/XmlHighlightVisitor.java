/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.BundleBase;
import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.IdeValidationHost;
import com.intellij.codeInsight.daemon.Validator;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixActionRegistrarImpl;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInspection.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceOwner;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.xml.*;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.AnchorReference;
import com.intellij.xml.util.XmlTagUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mike
 */
public class XmlHighlightVisitor extends XmlElementVisitor implements HighlightVisitor, IdeValidationHost {
  private static final Logger LOG = Logger.getInstance("com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor");
  private static boolean ourDoJaxpTesting;

  private static final TextAttributes NONEMPTY_TEXT_ATTRIBUTES = new TextAttributes() {
    @Override
    public boolean isEmpty() {
      return false;
    }
  };
  private HighlightInfoHolder myHolder;

  public XmlHighlightVisitor() {
  }

  private void addElementsForTag(XmlTag tag,
                                 @NotNull String localizedMessage,
                                 HighlightInfoType type) {
    addElementsForTagWithManyQuickFixes(tag, localizedMessage, type, (IntentionAction)null);
  }

  private void addElementsForTagWithManyQuickFixes(XmlTag tag,
                                                   @NotNull String localizedMessage,
                                                   HighlightInfoType type, IntentionAction... quickFixActions) {
    bindMessageToTag(tag, type, -1, localizedMessage, quickFixActions);
  }

  @Override public void visitXmlToken(XmlToken token) {
    IElementType tokenType = token.getTokenType();
    if (tokenType == XmlTokenType.XML_NAME || tokenType == XmlTokenType.XML_TAG_NAME) {
      PsiElement element = token.getPrevSibling();
      while(element instanceof PsiWhiteSpace) element = element.getPrevSibling();

      if (element instanceof XmlToken) {
        if (((XmlToken)element).getTokenType() == XmlTokenType.XML_START_TAG_START) {
          PsiElement parent = element.getParent();

          if (parent instanceof XmlTag && !(token.getNextSibling() instanceof OuterLanguageElement)) {
            checkTag((XmlTag)parent);
          }
        }
      } else {
        PsiElement parent = token.getParent();

        if (parent instanceof XmlAttribute && !(token.getNextSibling() instanceof OuterLanguageElement)) {
          checkAttribute((XmlAttribute) parent);
        }
      }
    } else if (tokenType == XmlTokenType.XML_DATA_CHARACTERS && token.getParent() instanceof XmlText) {
      if (token.textContains(']') && token.textContains('>')) {

        String s = token.getText();
        String marker = "]]>";
        int i = s.indexOf(marker);

        if (i != -1 ) {                              // TODO: fix
          XmlTag tag = PsiTreeUtil.getParentOfType(token, XmlTag.class);
          if (tag != null && XmlExtension.getExtensionByElement(tag).shouldBeHighlightedAsTag(tag) && !skipValidation(tag)) {
            TextRange textRange = token.getTextRange();
            int start = textRange.getStartOffset() + i;
            HighlightInfoType type = tag instanceof HtmlTag ? HighlightInfoType.WARNING : HighlightInfoType.ERROR;
            String description = XmlErrorMessages.message("cdata.end.should.not.appear.in.content.unless.to.mark.end.of.cdata.section");
            HighlightInfo info = HighlightInfo.newHighlightInfo(type).range(start, start + marker.length()).descriptionAndTooltip(description).create();
            addToResults(info);
          }
        }
      }
    }
  }

  private void checkTag(XmlTag tag) {
    if (ourDoJaxpTesting) return;

    if (!myHolder.hasErrorResults()) {
      checkTagByDescriptor(tag);
    }

    if (!myHolder.hasErrorResults()) {
      if (!skipValidation(tag)) {
        final XmlElementDescriptor descriptor = tag.getDescriptor();

        if (tag instanceof HtmlTag &&
            ( descriptor instanceof AnyXmlElementDescriptor ||
              descriptor == null
            )
           ) {
          return;
        }

        checkReferences(tag);
      }
    }
  }

  private void bindMessageToTag(final XmlTag tag,
                                final HighlightInfoType warning,
                                final int messageLength,
                                @NotNull String localizedMessage,
                                IntentionAction... quickFixActions) {
    XmlToken childByRole = XmlTagUtil.getStartTagNameElement(tag);

    bindMessageToAstNode(childByRole, warning, messageLength, localizedMessage, quickFixActions);
    childByRole = XmlTagUtil.getEndTagNameElement(tag);
    bindMessageToAstNode(childByRole, warning, messageLength, localizedMessage, quickFixActions);
  }


  @Override
  public void visitXmlProcessingInstruction(XmlProcessingInstruction processingInstruction) {
    super.visitXmlProcessingInstruction(processingInstruction);
    PsiElement parent = processingInstruction.getParent();

    if (parent instanceof XmlProlog && processingInstruction.getText().startsWith("<?xml")) {
      for(PsiElement e = PsiTreeUtil.prevLeaf(processingInstruction); e != null; e = PsiTreeUtil.prevLeaf(e)) {
        if (e instanceof PsiWhiteSpace && PsiTreeUtil.prevLeaf(e) != null ||
            e instanceof OuterLanguageElement) {
          continue;
        }
        PsiElement eParent = e.getParent();
        if (eParent instanceof PsiComment) e = eParent;
        if (eParent instanceof XmlProcessingInstruction) break;

        String description = XmlErrorMessages.message("xml.declaration.should.precede.all.document.content");
        addToResults(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(e).descriptionAndTooltip(description).create());
      }
    }
    checkReferences(processingInstruction);
  }

  private void bindMessageToAstNode(final PsiElement childByRole,
                                    final HighlightInfoType warning,
                                    int length,
                                    @NotNull String localizedMessage,
                                    IntentionAction... quickFixActions) {
    if(childByRole != null) {
      final TextRange textRange = childByRole.getTextRange();
      if (length == -1) length = textRange.getLength();
      final int startOffset = textRange.getStartOffset();

      HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(warning).range(childByRole, startOffset, startOffset + length).descriptionAndTooltip(localizedMessage).create();

      if (highlightInfo == null) {
        highlightInfo = HighlightInfo.newHighlightInfo(warning).range(new TextRange(startOffset, startOffset + length)).textAttributes(NONEMPTY_TEXT_ATTRIBUTES).descriptionAndTooltip(localizedMessage).create();
      }

      for (final IntentionAction quickFixAction : quickFixActions) {
        if (quickFixAction == null) continue;
        QuickFixAction.registerQuickFixAction(highlightInfo, textRange, quickFixAction);
      }
      addToResults(highlightInfo);
    }
  }

  private void checkTagByDescriptor(final XmlTag tag) {
    String name = tag.getName();

    XmlElementDescriptor elementDescriptor;

    final PsiElement parent = tag.getParent();
    if (parent instanceof XmlTag) {
      XmlTag parentTag = (XmlTag)parent;

      elementDescriptor = XmlUtil.getDescriptorFromContext(tag);

      final XmlElementDescriptor parentDescriptor = parentTag.getDescriptor();

      if (parentDescriptor != null && elementDescriptor == null && shouldBeValidated(tag)) {
        if (tag instanceof HtmlTag) {
          //XmlEntitiesInspection inspection = getInspectionProfile(tag, HtmlStyleLocalInspection.SHORT_NAME);
          //if (inspection != null /*&& isAdditionallyDeclared(inspection.getAdditionalEntries(XmlEntitiesInspection.UNKNOWN_TAG), name)*/) {
            return;
          //}
        }

        addElementsForTag(
          tag,
          XmlErrorMessages.message("element.is.not.allowed.here", name),
          getTagProblemInfoType(tag)
        );
        return;
      }

      if (elementDescriptor instanceof AnyXmlElementDescriptor ||
          elementDescriptor == null
         ) {
        elementDescriptor = tag.getDescriptor();
      }

      if (elementDescriptor == null) return;
    }
    else {
      //root tag
      elementDescriptor = tag.getDescriptor();

     if (elementDescriptor == null) {
       addElementsForTag(tag, XmlErrorMessages.message("element.must.be.declared", name), HighlightInfoType.WRONG_REF);
       return;
      }
    }

    if (elementDescriptor instanceof Validator) {
      //noinspection unchecked
      ((Validator<XmlTag>)elementDescriptor).validate(tag,this);
    }
  }

  private static HighlightInfoType getTagProblemInfoType(XmlTag tag) {
    if (tag instanceof HtmlTag && XmlUtil.HTML_URI.equals(tag.getNamespace())) {
      if (isInjectedWithoutValidation(tag)) return HighlightInfoType.INFORMATION;
      return HighlightInfoType.WARNING;
    }
    return HighlightInfoType.WRONG_REF;
  }

  public static boolean isInjectedWithoutValidation(PsiElement element) {
    return InjectedLanguageManager.FRANKENSTEIN_INJECTION.get(element.getContainingFile()) == Boolean.TRUE;
  }

  public static boolean skipValidation(PsiElement context) {
    return context instanceof XmlElement && ((XmlElement)context).skipValidation();
  }

  public static void setSkipValidation(@NotNull PsiElement element) {
    element.putUserData(XmlElement.DO_NOT_VALIDATE, Boolean.TRUE);
  }

  @Override public void visitXmlAttribute(XmlAttribute attribute) {}

  private void checkAttribute(XmlAttribute attribute) {
    XmlTag tag = attribute.getParent();
    if (tag == null) return;

    final String name = attribute.getName();
    PsiElement prevLeaf = PsiTreeUtil.prevLeaf(attribute);

    if (!(prevLeaf instanceof PsiWhiteSpace)) {
      TextRange textRange = attribute.getTextRange();
      HighlightInfoType type = tag instanceof HtmlTag ? HighlightInfoType.WARNING : HighlightInfoType.ERROR;
      String description = XmlErrorMessages.message("attribute.should.be.preceded.with.space");
      HighlightInfo info = HighlightInfo.newHighlightInfo(type).range(textRange.getStartOffset(), textRange.getStartOffset()).descriptionAndTooltip(description).create();
      addToResults(info);
    }

    if (attribute.isNamespaceDeclaration() || XmlUtil.XML_SCHEMA_INSTANCE_URI.equals(attribute.getNamespace())) {
      //checkReferences(attribute.getValueElement());
      return;
    }

    XmlElementDescriptor elementDescriptor = tag.getDescriptor();
    if (elementDescriptor == null ||
        elementDescriptor instanceof AnyXmlElementDescriptor ||
        ourDoJaxpTesting) {
      return;
    }

    XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor(attribute);

    if (attributeDescriptor == null) {
      if (!XmlUtil.attributeFromTemplateFramework(name, tag)) {
        final String localizedMessage = XmlErrorMessages.message("attribute.is.not.allowed.here", name);
        final HighlightInfo highlightInfo = reportAttributeProblem(tag, name, attribute, localizedMessage);
        if (highlightInfo != null) {
          PsiFile file = tag.getContainingFile();
          if (file != null) {
            for (XmlUndefinedElementFixProvider fixProvider : Extensions.getExtensions(XmlUndefinedElementFixProvider.EP_NAME)) {
              IntentionAction[] fixes = fixProvider.createFixes(attribute);
              if (fixes != null) {
                for (IntentionAction action : fixes) {
                  QuickFixAction.registerQuickFixAction(highlightInfo, action);
                }
                break;
              }
            }
          }
        }
      }
    }
    else {
      checkDuplicateAttribute(tag, attribute);

      // we skip resolve of attribute references since there is separate check when taking attribute descriptors
      PsiReference[] attrRefs = attribute.getReferences();
      doCheckRefs(attribute, attrRefs, !attribute.getNamespacePrefix().isEmpty() ? 2 : 1);
    }
  }

  @Nullable
  private HighlightInfo reportAttributeProblem(final XmlTag tag,
                                               final String localName,
                                               final XmlAttribute attribute,
                                               @NotNull String localizedMessage) {

    final RemoveAttributeIntentionFix removeAttributeIntention = new RemoveAttributeIntentionFix(localName);

    if (!(tag instanceof HtmlTag)) {
      final HighlightInfoType tagProblemInfoType = HighlightInfoType.WRONG_REF;

      final ASTNode node = SourceTreeToPsiMap.psiElementToTree(attribute);
      assert node != null;
      final ASTNode child = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(node);
      assert child != null;
      final HighlightInfo highlightInfo =
        HighlightInfo.newHighlightInfo(tagProblemInfoType).range(child).descriptionAndTooltip(localizedMessage).create();
      addToResults(highlightInfo);

      QuickFixAction.registerQuickFixAction(highlightInfo, removeAttributeIntention);

      return highlightInfo;
    }

    return null;
  }

  private void checkDuplicateAttribute(XmlTag tag, final XmlAttribute attribute) {
    if (skipValidation(tag)) {
      return;
    }

    final XmlAttribute[] attributes = tag.getAttributes();
    final PsiFile containingFile = tag.getContainingFile();
    final XmlExtension extension = containingFile instanceof XmlFile ?
                                   XmlExtension.getExtension(containingFile) :
                                   DefaultXmlExtension.DEFAULT_EXTENSION;
    for (XmlAttribute tagAttribute : attributes) {
      ProgressManager.checkCanceled();
      if (attribute != tagAttribute && Comparing.strEqual(attribute.getName(), tagAttribute.getName())) {
        final String localName = attribute.getLocalName();

        if (extension.canBeDuplicated(tagAttribute)) continue; // multiple import attributes are allowed in jsp directive

        final ASTNode attributeNode = SourceTreeToPsiMap.psiElementToTree(attribute);
        assert attributeNode != null;
        final ASTNode attributeNameNode = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(attributeNode);
        assert attributeNameNode != null;
        HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(getTagProblemInfoType(tag))
          .range(attributeNameNode)
          .descriptionAndTooltip(XmlErrorMessages.message("duplicate.attribute", localName)).create();
        addToResults(highlightInfo);

        IntentionAction intentionAction = new RemoveAttributeIntentionFix(localName);

        QuickFixAction.registerQuickFixAction(highlightInfo, intentionAction);
      }
    }
  }

  @Override public void visitXmlDocument(final XmlDocument document) {
    if (document.getLanguage() == DTDLanguage.INSTANCE) {
      final PsiMetaData psiMetaData = document.getMetaData();
      if (psiMetaData instanceof Validator) {
        //noinspection unchecked
        ((Validator<XmlDocument>)psiMetaData).validate(document, this);
      }
    }
  }

  @Override public void visitXmlTag(XmlTag tag) {
  }

  @Override public void visitXmlAttributeValue(XmlAttributeValue value) {
    checkReferences(value);

    final PsiElement parent = value.getParent();
    if (!(parent instanceof XmlAttribute)) {
      return;
    }

    XmlAttribute attribute = (XmlAttribute)parent;

    XmlTag tag = attribute.getParent();

    XmlElementDescriptor elementDescriptor = tag.getDescriptor();
    XmlAttributeDescriptor attributeDescriptor = elementDescriptor != null ? elementDescriptor.getAttributeDescriptor(attribute):null;

    if (attributeDescriptor != null && !skipValidation(value)) {
      String error = attributeDescriptor.validateValue(value, attribute.getValue());

      if (error != null) {
        HighlightInfoType type = getTagProblemInfoType(tag);
        addToResults(HighlightInfo.newHighlightInfo(type).range(value).descriptionAndTooltip(error).create());
      }
    }
  }

  private void checkReferences(PsiElement value) {
    if (value == null) return;

    doCheckRefs(value, value.getReferences(), 0);
  }

  private void doCheckRefs(final PsiElement value, final PsiReference[] references, int start) {
    for (int i = start; i < references.length; ++i) {
      PsiReference reference = references[i];
      ProgressManager.checkCanceled();
      if (isUrlReference(reference)) continue;
      if (!hasBadResolve(reference, false)) {
        continue;
      }
      String description = getErrorDescription(reference);

      final int startOffset = reference.getElement().getTextRange().getStartOffset();
      final TextRange referenceRange = reference.getRangeInElement();

      // logging for IDEADEV-29655
      if (referenceRange.getStartOffset() > referenceRange.getEndOffset()) {
        LOG.error("Reference range start offset > end offset:  " + reference +
        ", start offset: " + referenceRange.getStartOffset() + ", end offset: " + referenceRange.getEndOffset());
      }

      HighlightInfoType type = getTagProblemInfoType(PsiTreeUtil.getParentOfType(value, XmlTag.class));
      if (value instanceof XmlAttributeValue) {
        PsiElement parent = value.getParent();
        if (parent instanceof XmlAttribute) {
          String name = ((XmlAttribute)parent).getName().toLowerCase();
          if (type.getSeverity(null).compareTo(HighlightInfoType.WARNING.getSeverity(null)) > 0 && name.endsWith("stylename")) {
            type = HighlightInfoType.WARNING;
          }
        }
      }
      HighlightInfo info = HighlightInfo.newHighlightInfo(type)
        .range(startOffset + referenceRange.getStartOffset(), startOffset + referenceRange.getEndOffset())
        .descriptionAndTooltip(description).create();
      addToResults(info);
      if (reference instanceof LocalQuickFixProvider) {
        LocalQuickFix[] fixes = ((LocalQuickFixProvider)reference).getQuickFixes();
        if (fixes != null) {
          InspectionManager manager = InspectionManager.getInstance(reference.getElement().getProject());
          for (LocalQuickFix fix : fixes) {
            ProblemDescriptor descriptor = manager.createProblemDescriptor(value, description, fix,
                                                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true);
            QuickFixAction.registerQuickFixAction(info, new LocalQuickFixAsIntentionAdapter(fix, descriptor));
          }
        }
      }
      UnresolvedReferenceQuickFixProvider.registerReferenceFixes(reference, new QuickFixActionRegistrarImpl(info));
    }
  }

  public static boolean isUrlReference(PsiReference reference) {
    return reference instanceof FileReferenceOwner || reference instanceof AnchorReference;
  }

  @NotNull
  public static String getErrorDescription(@NotNull PsiReference reference) {
    String message;
    if (reference instanceof EmptyResolveMessageProvider) {
      message = ((EmptyResolveMessageProvider)reference).getUnresolvedMessagePattern();
    }
    else {
      //noinspection UnresolvedPropertyKey
      message = PsiBundle.message("cannot.resolve.symbol");
    }

    String description;
    try {
      description = BundleBase.format(message, reference.getCanonicalText()); // avoid double formatting
    }
    catch (IllegalArgumentException ex) {
      // unresolvedMessage provided by third-party reference contains wrong format string (e.g. {}), tolerate it
      description = message;
    }
    return description;
  }

  public static boolean hasBadResolve(final PsiReference reference, boolean checkSoft) {
    if (!checkSoft && reference.isSoft()) return false;
    if (reference instanceof PsiPolyVariantReference) {
      return ((PsiPolyVariantReference)reference).multiResolve(false).length == 0;
    }
    return reference.resolve() == null;
  }

  @Override public void visitXmlDoctype(XmlDoctype xmlDoctype) {
    if (skipValidation(xmlDoctype)) return;
    checkReferences(xmlDoctype);
  }

  private void addToResults(final HighlightInfo info) {
    myHolder.add(info);
  }

  public static void setDoJaxpTesting(boolean doJaxpTesting) {
    ourDoJaxpTesting = doJaxpTesting;
  }

  @Override
  public void addMessage(PsiElement context, String message, int type) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addMessage(PsiElement context, String message, @NotNull ErrorType type) {
    addMessageWithFixes(context, message, type);
  }

  @Override
  public void addMessageWithFixes(final PsiElement context, final String message, @NotNull final ErrorType type, @NotNull final IntentionAction... fixes) {
    if (message != null && !message.isEmpty()) {
      final PsiFile containingFile = context.getContainingFile();
      final HighlightInfoType defaultInfoType = type == ErrorType.ERROR ? HighlightInfoType.ERROR : type == ErrorType.WARNING ? HighlightInfoType.WARNING : HighlightInfoType.WEAK_WARNING;

      if (context instanceof XmlTag && XmlExtension.getExtension(containingFile).shouldBeHighlightedAsTag((XmlTag)context)) {
        addElementsForTagWithManyQuickFixes((XmlTag)context, message, defaultInfoType, fixes);
      }
      else {
        final PsiElement contextOfFile = InjectedLanguageManager.getInstance(containingFile.getProject()).getInjectionHost(containingFile);
        final HighlightInfo highlightInfo;

        if (contextOfFile != null) {
          TextRange range = InjectedLanguageManager.getInstance(context.getProject()).injectedToHost(context, context.getTextRange());
          highlightInfo = HighlightInfo.newHighlightInfo(defaultInfoType).range(range).descriptionAndTooltip(message).create();
        }
        else {
          highlightInfo =
            HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(context).descriptionAndTooltip(message).create();
        }

        for (final IntentionAction quickFixAction : fixes) {
          if (quickFixAction == null) continue;
          QuickFixAction.registerQuickFixAction(highlightInfo, quickFixAction);
        }
        addToResults(highlightInfo);
      }
    }
  }

  @Override
  public boolean suitableForFile(@NotNull final PsiFile file) {
    if (file instanceof XmlFile) return true;

    for (PsiFile psiFile : file.getViewProvider().getAllFiles()) {
      if (psiFile instanceof XmlFile) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void visit(@NotNull final PsiElement element) {
    element.accept(this);
  }

  @Override
  public boolean analyze(@NotNull final PsiFile file,
                         final boolean updateWholeFile,
                         @NotNull HighlightInfoHolder holder,
                         @NotNull Runnable action) {
    myHolder = holder;
    try {
      action.run();
    }
    finally {
      myHolder = null;
    }
    return true;
  }

  @Override
  @NotNull
  public HighlightVisitor clone() {
    return new XmlHighlightVisitor();
  }

  @Override
  public int order() {
    return 1;
  }

  public static String getUnquotedValue(XmlAttributeValue value, XmlTag tag) {
    String unquotedValue = value.getValue();

    if (tag instanceof HtmlTag) {
      unquotedValue = unquotedValue.toLowerCase();
    }

    return unquotedValue;
  }

  public static boolean shouldBeValidated(@NotNull XmlTag tag) {
    PsiElement parent = tag.getParent();
    if (parent instanceof XmlTag) {
      return !skipValidation(parent) && !XmlUtil.tagFromTemplateFramework(tag);
    }
    return true;
  }
}
