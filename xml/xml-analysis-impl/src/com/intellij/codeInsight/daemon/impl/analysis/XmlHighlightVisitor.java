// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.BundleBase;
import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.IdeValidationHost;
import com.intellij.codeInsight.daemon.Validator;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.tagTreeHighlighting.XmlTagTreeHighlightingUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.markup.UnmodifiableTextAttributes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.*;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.xml.*;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.AnchorReference;
import com.intellij.xml.util.XmlTagUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class XmlHighlightVisitor extends XmlElementVisitor implements HighlightVisitor, IdeValidationHost {
  private static final Logger LOG = Logger.getInstance(XmlHighlightVisitor.class);

  private static boolean ourDoJaxpTesting;
  private boolean myHasError;

  private static class Holder {
    private static final TextAttributes NONEMPTY_TEXT_ATTRIBUTES = new UnmodifiableTextAttributes() {
      @Override
      public boolean isEmpty() {
        return false;
      }
    };
  }
  private HighlightInfoHolder myHolder;

  @Override
  public void visit(final @NotNull PsiElement element) {
    myHasError = false;
    element.accept(this);
  }

  @Override
  public boolean analyze(final @NotNull PsiFile file,
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

  private boolean add(@Nullable HighlightInfo info) {
    if (info != null) {
      if (info.getSeverity().compareTo(HighlightSeverity.ERROR) >= 0) {
        myHasError = true;
      }
      return myHolder.add(info);
    }
    return false;
  }
  @Contract(pure = true)
  private boolean hasErrorResults() {
    return myHasError;
  }

  private void addElementsForTagWithManyQuickFixes(XmlTag tag,
                                                   @NotNull @InspectionMessage String localizedMessage,
                                                   HighlightInfoType type, IntentionAction... quickFixActions) {
    XmlToken childByRole = XmlTagUtil.getStartTagNameElement(tag);

    bindMessageToAstNode(childByRole, type, localizedMessage, quickFixActions);
    childByRole = XmlTagUtil.getEndTagNameElement(tag);
    bindMessageToAstNode(childByRole, type, localizedMessage, quickFixActions);
  }

  @Override public void visitXmlToken(@NotNull XmlToken token) {
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
          if (tag != null
              && XmlExtension.getExtensionByElement(tag).shouldBeHighlightedAsTag(tag)
              && !skipValidation(tag)
              && !(tag instanceof HtmlTag)
          ) {
            TextRange textRange = token.getTextRange();
            int start = textRange.getStartOffset() + i;
            HighlightInfoType type = HighlightInfoType.ERROR;
            String description = XmlAnalysisBundle.message(
              "xml.inspections.cdata.end.should.not.appear.in.content");
            add(HighlightInfo.newHighlightInfo(type).range(start, start + marker.length()).descriptionAndTooltip(description).create());
          }
        }
      }
    }
  }

  private void checkTag(@NotNull XmlTag tag) {
    if (ourDoJaxpTesting) return;

    if (!hasErrorResults()) {
      checkTagByDescriptor(tag);
    }

    if (!hasErrorResults()) {
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


  @Override
  public void visitXmlProcessingInstruction(@NotNull XmlProcessingInstruction processingInstruction) {
    super.visitXmlProcessingInstruction(processingInstruction);
    PsiElement parent = processingInstruction.getParent();

    if (parent instanceof XmlProlog && processingInstruction.getText().startsWith("<?xml ")) {
      for(PsiElement e = PsiTreeUtil.prevLeaf(processingInstruction); e != null; e = PsiTreeUtil.prevLeaf(e)) {
        if (e instanceof PsiWhiteSpace && PsiTreeUtil.prevLeaf(e) != null ||
            e instanceof OuterLanguageElement) {
          continue;
        }
        PsiElement eParent = e.getParent();
        if (eParent instanceof PsiComment) e = eParent;
        if (eParent instanceof XmlProcessingInstruction) break;

        String description = XmlAnalysisBundle.message("xml.inspections.xml.declaration.should.precede.all.document.content");
        add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(e).descriptionAndTooltip(description).create());
      }
    }
    checkReferences(processingInstruction);
  }

  private void bindMessageToAstNode(final PsiElement childByRole,
                                    final HighlightInfoType warning,
                                    @NotNull @InspectionMessage String localizedMessage,
                                    IntentionAction... quickFixActions) {
    if(childByRole != null) {
      final TextRange textRange = childByRole.getTextRange();
      int length = textRange.getLength();
      final int startOffset = textRange.getStartOffset();

      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(warning).range(childByRole, startOffset, startOffset + length)
        .descriptionAndTooltip(localizedMessage);
      for (final IntentionAction quickFixAction : quickFixActions) {
        builder.registerFix(quickFixAction, null, null, textRange, null);
      }
      HighlightInfo highlightInfo = builder.create();
      if (highlightInfo == null) {
        builder = HighlightInfo.newHighlightInfo(warning).range(new TextRange(startOffset, startOffset + length))
          .textAttributes(Holder.NONEMPTY_TEXT_ATTRIBUTES);
        for (final IntentionAction quickFixAction : quickFixActions) {
          builder.registerFix(quickFixAction, null, null, textRange, null);
        }
        highlightInfo = builder.descriptionAndTooltip(localizedMessage).create();
      }

      add(highlightInfo);
    }
  }

  private void checkTagByDescriptor(final XmlTag tag) {
    String name = tag.getName();

    XmlElementDescriptor elementDescriptor;

    final PsiElement parent = tag.getParent();
    if (parent instanceof XmlTag parentTag) {

      elementDescriptor = XmlUtil.getDescriptorFromContext(tag);

      final XmlElementDescriptor parentDescriptor = parentTag.getDescriptor();

      if (parentDescriptor != null && elementDescriptor == null && shouldBeValidated(tag)) {
        if (tag instanceof HtmlTag) {
          //XmlEntitiesInspection inspection = getInspectionProfile(tag, HtmlStyleLocalInspection.SHORT_NAME);
          //if (inspection != null /*&& isAdditionallyDeclared(inspection.getAdditionalEntries(XmlEntitiesInspection.UNKNOWN_TAG), name)*/) {
            return;
          //}
        }

        HighlightInfoType type = getTagProblemInfoType(tag);
        addElementsForTagWithManyQuickFixes(tag, XmlAnalysisBundle.message("xml.inspections.element.is.not.allowed.here", name), type);
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
       addElementsForTagWithManyQuickFixes(tag, XmlAnalysisBundle.message("xml.inspections.element.must.be.declared", name),
                                           HighlightInfoType.WRONG_REF);
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
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(element.getProject());
    return injectedLanguageManager.isFrankensteinInjection(element)
      || injectedLanguageManager.shouldInspectionsBeLenient(element);
  }

  public static boolean skipValidation(PsiElement context) {
    return context instanceof XmlElement && ((XmlElement)context).skipValidation();
  }

  public static void setSkipValidation(@NotNull PsiElement element) {
    element.putUserData(XmlElement.DO_NOT_VALIDATE, Boolean.TRUE);
  }

  @Override public void visitXmlAttribute(@NotNull XmlAttribute attribute) {}

  private void checkAttribute(XmlAttribute attribute) {
    XmlTag tag = attribute.getParent();
    if (tag == null) return;

    final String name = attribute.getName();
    PsiElement prevLeaf = PsiTreeUtil.prevLeaf(attribute);

    if (!(prevLeaf instanceof PsiWhiteSpace)
        && (!(tag instanceof HtmlTag)
            || !XmlUtil.hasNonEditableInjectionFragmentAt(attribute, attribute.getTextOffset()))) {
      TextRange textRange = attribute.getTextRange();
      HighlightInfoType type = tag instanceof HtmlTag ? HighlightInfoType.WARNING : HighlightInfoType.ERROR;
      String description = XmlAnalysisBundle.message("xml.inspections.attribute.should.be.preceded.with.space");
      HighlightInfo info = HighlightInfo.newHighlightInfo(type).range(textRange.getStartOffset(), textRange.getStartOffset()).descriptionAndTooltip(description).create();
      add(info);
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

    XmlAttributeDescriptor attributeDescriptor = attribute.getDescriptor();

    if (attributeDescriptor == null) {
      if (!XmlUtil.attributeFromTemplateFramework(name, tag)) {
        final String localizedMessage = XmlAnalysisBundle.message("xml.inspections.attribute.is.not.allowed.here", name);
        reportAttributeProblem(tag, name, attribute, localizedMessage);
      }
    }
    else {
      checkDuplicateAttribute(tag, attribute);

      // we skip resolve of attribute references since there is separate check when taking attribute descriptors
      PsiReference[] attrRefs = attribute.getReferences();
      doCheckRefs(attribute, attrRefs, !attribute.getNamespacePrefix().isEmpty() ? 2 : 1);
    }
  }

  private void reportAttributeProblem(final XmlTag tag,
                                               final String localName,
                                               final XmlAttribute attribute,
                                               @NotNull @InspectionMessage String localizedMessage) {

    final RemoveAttributeIntentionFix removeAttributeIntention = new RemoveAttributeIntentionFix(localName);

    if (tag instanceof HtmlTag) {
      return;
    }
    final HighlightInfoType tagProblemInfoType = HighlightInfoType.WRONG_REF;

    final ASTNode node = SourceTreeToPsiMap.psiElementToTree(attribute);
    assert node != null;
    final ASTNode child = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(node);
    assert child != null;
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(tagProblemInfoType)
      .range(child)
      .descriptionAndTooltip(localizedMessage)
      .registerFix(removeAttributeIntention, List.of(), null, null, null);
    PsiFile file = tag.getContainingFile();
    if (file != null) {
      for (XmlUndefinedElementFixProvider fixProvider : XmlUndefinedElementFixProvider.EP_NAME.getExtensionList()) {
        IntentionAction[] fixes = fixProvider.createFixes(attribute);
        if (fixes != null) {
          for (IntentionAction action : fixes) {
            builder.registerFix(action, null, null, null, null);
          }
          break;
        }
      }
    }
    final HighlightInfo highlightInfo = builder.create();
    add(highlightInfo);
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
        IntentionAction intentionAction = new RemoveAttributeIntentionFix(localName);
        HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(getTagProblemInfoType(tag))
          .range(attributeNameNode)
          .registerFix(intentionAction, List.of(), null, null, null)
          .descriptionAndTooltip(XmlAnalysisBundle.message("xml.inspections.duplicate.attribute", localName)).create();
        add(highlightInfo);
      }
    }
  }

  @Override public void visitXmlDocument(final @NotNull XmlDocument document) {
    if (document.getLanguage() == DTDLanguage.INSTANCE) {
      final PsiMetaData psiMetaData = document.getMetaData();
      if (psiMetaData instanceof Validator) {
        //noinspection unchecked
        ((Validator<XmlDocument>)psiMetaData).validate(document, this);
      }
    }
  }

  @Override public void visitXmlTag(@NotNull XmlTag tag) {
  }

  @Override public void visitXmlAttributeValue(@NotNull XmlAttributeValue value) {
    checkReferences(value);

    final PsiElement parent = value.getParent();
    if (!(parent instanceof XmlAttribute attribute)) {
      return;
    }

    XmlTag tag = attribute.getParent();

    if (tag == null) {
      LOG.error("Missing tag for attribute: " + attribute.getName(),
                new Attachment(myHolder.getContextFile().getName(), myHolder.getContextFile().getText()));
      return;
    }

    XmlAttributeDescriptor attributeDescriptor = attribute.getDescriptor();

    if (attributeDescriptor != null && !skipValidation(value)) {
      String error = attributeDescriptor.validateValue(value, attribute.getValue());

      if (error != null) {
        HighlightInfoType type = getTagProblemInfoType(tag);
        if (error.startsWith("<html>")) {
          add(HighlightInfo.newHighlightInfo(type).range(value)
                         .description(StringUtil.removeHtmlTags(error).replace("\n", " "))
                         .escapedToolTip(error).create());
        }
        else {
          add(HighlightInfo.newHighlightInfo(type).range(value).descriptionAndTooltip(error).create());
        }
      }
    }
  }

  private void checkReferences(PsiElement value) {
    if (value == null) return;

    doCheckRefs(value, value.getReferences(), 0);
  }

  private void doCheckRefs(@NotNull PsiElement value, final PsiReference @NotNull [] references, int start) {
    for (int i = start; i < references.length; ++i) {
      PsiReference reference = references[i];
      ProgressManager.checkCanceled();
      if (!shouldCheckResolve(reference)) continue;
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
          String name = StringUtil.toLowerCase(((XmlAttribute)parent).getName());
          if (type.getSeverity(null).compareTo(HighlightInfoType.WARNING.getSeverity(null)) > 0 && name.endsWith("stylename")) {
            type = HighlightInfoType.WARNING;
          }
        }
      }
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(type)
        .range(startOffset + referenceRange.getStartOffset(), startOffset + referenceRange.getEndOffset())
        .descriptionAndTooltip(description);
      if (reference instanceof LocalQuickFixProvider) {
        LocalQuickFix[] fixes = ((LocalQuickFixProvider)reference).getQuickFixes();
        if (fixes != null) {
          InspectionManager manager = InspectionManager.getInstance(reference.getElement().getProject());
          for (LocalQuickFix fix : fixes) {
            ProblemDescriptor descriptor = manager.createProblemDescriptor(value, description, fix,
                                                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true);
            builder.registerFix(QuickFixWrapper.wrap(descriptor, fix), null, null, null, null);
          }
        }
      }
      UnresolvedReferenceQuickFixProvider.registerUnresolvedReferenceLazyQuickFixes(reference, builder);
      add(builder.create());
    }
  }

  static boolean shouldCheckResolve(PsiReference reference) {
    return reference instanceof TypeOrElementOrAttributeReference ||
           reference instanceof DependentNSReference ||
           reference instanceof URLReference ||
           reference instanceof TagNameReference ||
           reference instanceof PsiReferenceWithUnresolvedQuickFixes;
  }

  static boolean isUrlReference(PsiReference reference) {
    return reference instanceof FileReferenceOwner || reference instanceof AnchorReference || reference instanceof PsiFileReference;
  }

  public static @NotNull @InspectionMessage String getErrorDescription(@NotNull PsiReference reference) {
    @InspectionMessage String message;
    if (reference instanceof EmptyResolveMessageProvider) {
      message = ((EmptyResolveMessageProvider)reference).getUnresolvedMessagePattern();
    }
    else {
      //noinspection UnresolvedPropertyKey
      message = AnalysisBundle.message("cannot.resolve.symbol");
    }

    String description;
    try {
      //noinspection HardCodedStringLiteral
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

  @Override public void visitXmlDoctype(@NotNull XmlDoctype xmlDoctype) {
    if (skipValidation(xmlDoctype)) return;
    checkReferences(xmlDoctype);
  }

  public static void setDoJaxpTesting(boolean doJaxpTesting) {
    ourDoJaxpTesting = doJaxpTesting;
  }

  @Override
  public void addMessage(PsiElement context, String message, @NotNull ErrorType type) {
    addMessageWithFixes(context, message, type);
  }

  @Override
  public void addMessageWithFixes(final PsiElement context, final String message, final @NotNull ErrorType type, final IntentionAction @NotNull ... fixes) {
    if (message != null && !message.isEmpty()) {
      final PsiFile containingFile = context.getContainingFile();
      final HighlightInfoType defaultInfoType = type == ErrorType.ERROR ? HighlightInfoType.ERROR : type == ErrorType.WARNING ? HighlightInfoType.WARNING : HighlightInfoType.WEAK_WARNING;

      if (context instanceof XmlTag && XmlExtension.getExtension(containingFile).shouldBeHighlightedAsTag((XmlTag)context)) {
        addElementsForTagWithManyQuickFixes((XmlTag)context, message, defaultInfoType, fixes);
      }
      else {
        final PsiElement contextOfFile = InjectedLanguageManager.getInstance(containingFile.getProject()).getInjectionHost(containingFile);
        HighlightInfo.Builder builder;

        if (contextOfFile != null) {
          TextRange range = InjectedLanguageManager.getInstance(context.getProject()).injectedToHost(context, context.getTextRange());
          builder = HighlightInfo.newHighlightInfo(defaultInfoType).range(range).descriptionAndTooltip(message);
        }
        else {
          builder = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(context).descriptionAndTooltip(message);
        }

        for (final IntentionAction quickFixAction : fixes) {
          if (quickFixAction == null) continue;
          builder.registerFix(quickFixAction, null, null, null, null);
        }
        add(builder.create());
      }
    }
  }

  @Override
  public boolean suitableForFile(final @NotNull PsiFile file) {
    return file instanceof XmlFile || XmlTagTreeHighlightingUtil.hasXmlViewProvider(file);
  }

  @Override
  public @NotNull HighlightVisitor clone() {
    return new XmlHighlightVisitor();
  }


  public static String getUnquotedValue(XmlAttributeValue value, XmlTag tag) {
    String unquotedValue = value.getValue();

    if (tag instanceof HtmlTag) {
      unquotedValue = StringUtil.toLowerCase(unquotedValue);
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
