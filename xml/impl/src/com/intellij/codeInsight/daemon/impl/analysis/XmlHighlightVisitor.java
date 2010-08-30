/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.*;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixActionRegistrarImpl;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.codeInspection.htmlInspections.XmlEntitiesInspection;
import com.intellij.idea.LoggerFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.SmartList;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlTagUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * @author Mike
 */
public class XmlHighlightVisitor extends XmlElementVisitor implements HighlightVisitor, Validator.ValidationHost {
  private static final Logger LOG = LoggerFactory.getInstance().getLoggerInstance("com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor");
  public static final Key<String> DO_NOT_VALIDATE_KEY = Key.create("do not validate");
  private List<HighlightInfo> myResult;

  private static boolean ourDoJaxpTesting;

  private static final TextAttributes NONEMPTY_TEXT_ATTRIBUTES = new TextAttributes() {
    public boolean isEmpty() {
      return false;
    }
  };

  private void addElementsForTag(XmlTag tag,
                                 String localizedMessage,
                                 HighlightInfoType type,
                                 IntentionAction quickFixAction) {
    addElementsForTagWithManyQuickFixes(tag, localizedMessage, type, quickFixAction);
  }

  private void addElementsForTagWithManyQuickFixes(XmlTag tag,
                                                   String localizedMessage,
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
        
        if (i != -1) {                              // TODO: fix
          TextRange textRange = token.getTextRange();
          int start = textRange.getStartOffset() + i;
          HighlightInfoType type = PsiTreeUtil.getParentOfType(token, XmlTag.class) instanceof HtmlTag ?
                                   HighlightInfoType.WARNING : HighlightInfoType.ERROR;
          HighlightInfo info = HighlightInfo.createHighlightInfo(
            type,
            start, start + marker.length(),
            XmlErrorMessages.message("cdata.end.should.not.appear.in.content.unless.to.mark.end.of.cdata.section")
          );
          addToResults(info);
        }
      }
    }
  }

  private void checkTag(XmlTag tag) {
    if (ourDoJaxpTesting) return;

    if (myResult == null) {
      checkTagByDescriptor(tag);
    }

    if (myResult == null) {
      if (tag.getUserData(DO_NOT_VALIDATE_KEY) == null) {
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

  private void bindMessageToTag(final XmlTag tag, final HighlightInfoType warning, final int messageLength, final String localizedMessage, IntentionAction... quickFixActions) {
    XmlToken childByRole = XmlTagUtil.getStartTagNameElement(tag);

    bindMessageToAstNode(childByRole, warning, 0, messageLength, localizedMessage, quickFixActions);
    childByRole = XmlTagUtil.getEndTagNameElement(tag);
    bindMessageToAstNode(childByRole, warning, 0, messageLength, localizedMessage, quickFixActions);
  }


  @Override
  public void visitXmlProcessingInstruction(XmlProcessingInstruction processingInstruction) {
    super .visitXmlProcessingInstruction(processingInstruction);
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
  
        addToResults(HighlightInfo.createHighlightInfo(
          HighlightInfoType.ERROR,
          e,
          XmlErrorMessages.message("xml.declaration.should.precede.all.document.content")
        ));
      }
    }
  }

  private void bindMessageToAstNode(final PsiElement childByRole,
                                    final HighlightInfoType warning,
                                    final int offset,
                                    int length,
                                    final String localizedMessage, IntentionAction... quickFixActions) {
    if(childByRole != null) {
      final TextRange textRange = childByRole.getTextRange();
      if (length == -1) length = textRange.getLength();
      final int startOffset = textRange.getStartOffset() + offset;

      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(
        warning,
        childByRole, startOffset, startOffset + length,
        localizedMessage, HighlightInfo.htmlEscapeToolTip(localizedMessage)
      );

      if (highlightInfo == null) {
        highlightInfo = HighlightInfo.createHighlightInfo(
          warning,
          new TextRange(startOffset, startOffset + length),
          localizedMessage,
          localizedMessage, NONEMPTY_TEXT_ATTRIBUTES
        );
      }

      for (final IntentionAction quickFixAction : quickFixActions) {
        if (quickFixAction == null) continue;
        QuickFixAction.registerQuickFixAction(highlightInfo, textRange, quickFixAction, null);
      }
      addToResults(highlightInfo);
    }
  }

  private void checkTagByDescriptor(final XmlTag tag) {
    String name = tag.getName();

    XmlElementDescriptor elementDescriptor = null;

    final PsiElement parent = tag.getParent();
    if (parent instanceof XmlTag) {
      XmlTag parentTag = (XmlTag)parent;
      final XmlElementDescriptor parentDescriptor = parentTag.getDescriptor();

      if (parentDescriptor != null) {
        elementDescriptor = XmlExtension.getExtension(tag.getContainingFile()).getElementDescriptor(tag, parentTag, parentDescriptor);
      }

      if (parentDescriptor != null &&
          elementDescriptor == null &&
          parentTag.getUserData(DO_NOT_VALIDATE_KEY) == null &&
          !XmlUtil.tagFromTemplateFramework(tag)
      ) {
        if (tag instanceof HtmlTag) {
          //XmlEntitiesInspection inspection = getInspectionProfile(tag, HtmlStyleLocalInspection.SHORT_NAME);
          //if (inspection != null /*&& isAdditionallyDeclared(inspection.getAdditionalEntries(XmlEntitiesInspection.UNKNOWN_TAG), name)*/) {
            return;
          //}
        }

        addElementsForTag(
          tag,
          XmlErrorMessages.message("element.is.not.allowed.here", name),
          getTagProblemInfoType(tag),
          null
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
       addElementsForTag(tag, XmlErrorMessages.message("element.must.be.declared", name), HighlightInfoType.WRONG_REF, null);
       return;
      }
    }

    XmlAttributeDescriptor[] attributeDescriptors = elementDescriptor.getAttributesDescriptors(tag);
    Set<String> requiredAttributes = null;

    for (XmlAttributeDescriptor attribute : attributeDescriptors) {
      if (attribute != null && attribute.isRequired()) {
        if (requiredAttributes == null) {
          requiredAttributes = new HashSet<String>();
        }
        requiredAttributes.add(attribute.getName(tag));
      }
    }

    if (requiredAttributes != null) {
      for (final String attrName : requiredAttributes) {
        if (tag.getAttribute(attrName, "") == null &&
            !XmlExtension.getExtension(tag.getContainingFile()).isRequiredAttributeImplicitlyPresent(tag, attrName)) {

          final InsertRequiredAttributeFix insertRequiredAttributeIntention = new InsertRequiredAttributeFix(
              tag, attrName, null);
          final String localizedMessage = XmlErrorMessages.message("element.doesnt.have.required.attribute", name, attrName);
          final InspectionProfile profile = InspectionProjectProfileManager.getInstance(tag.getProject()).getInspectionProfile();
          final LocalInspectionToolWrapper toolWrapper =
            (LocalInspectionToolWrapper)profile.getInspectionTool(RequiredAttributesInspection.SHORT_NAME, tag);
          if (toolWrapper != null) {
            RequiredAttributesInspection inspection = (RequiredAttributesInspection)toolWrapper.getTool();
            reportOneTagProblem(
              tag,
              attrName,
              localizedMessage,
              insertRequiredAttributeIntention,
              HighlightDisplayKey.find(RequiredAttributesInspection.SHORT_NAME),
              inspection,
              XmlEntitiesInspection.NOT_REQUIRED_ATTRIBUTE
            );
          }
        }
      }
    }

    if (elementDescriptor instanceof Validator) {
      //noinspection unchecked
      ((Validator<XmlTag>)elementDescriptor).validate(tag,this);
    }
  }

  private void reportOneTagProblem(final XmlTag tag,
                                   final String name,
                                   final String localizedMessage,
                                   final IntentionAction basicIntention,
                                   final HighlightDisplayKey key,
                                   final XmlEntitiesInspection inspection,
                                   final int type) {
    boolean htmlTag = false;

    if (tag instanceof HtmlTag) {
      htmlTag = true;
      if(isAdditionallyDeclared(inspection.getAdditionalEntries(type), name)) return;
    }

    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(tag.getProject()).getInspectionProfile();
    final IntentionAction intentionAction = inspection.getIntentionAction(name, type);
    if (htmlTag && profile.isToolEnabled(key, tag)) {
      addElementsForTagWithManyQuickFixes(
        tag,
        localizedMessage,
        isInjectedHtmlTagForWhichNoProblemsReporting((HtmlTag)tag) ?
          HighlightInfoType.INFORMATION :
          SeverityRegistrar.getInstance(tag.getProject()).getHighlightInfoTypeBySeverity(profile.getErrorLevel(key, tag).getSeverity()),
        intentionAction,
        basicIntention);
    } else if (!htmlTag) {
      addElementsForTag(
        tag,
        localizedMessage,
        HighlightInfoType.ERROR,
        basicIntention
      );
    }
  }

  private static boolean isAdditionallyDeclared(final String additional, String name) {
    name = name.toLowerCase();
    if (!additional.contains(name)) return false;

    StringTokenizer tokenizer = new StringTokenizer(additional, ", ");
    while (tokenizer.hasMoreTokens()) {
      if (name.equals(tokenizer.nextToken())) {
        return true;
      }
    }

    return false;
  }

  private static HighlightInfoType getTagProblemInfoType(XmlTag tag) {
    if (tag instanceof HtmlTag && XmlUtil.HTML_URI.equals(tag.getNamespace())) {
      if (isInjectedHtmlTagForWhichNoProblemsReporting((HtmlTag)tag)) return HighlightInfoType.INFORMATION;
      return HighlightInfoType.WARNING;
    }
    return HighlightInfoType.WRONG_REF;
  }

  private static boolean isInjectedHtmlTagForWhichNoProblemsReporting(HtmlTag tag) {
    PsiElement context = tag.getContainingFile().getContext();
    return context != null && skipValidation(context);
  }

  private static boolean skipValidation(PsiElement context) {
    return context.getUserData(DO_NOT_VALIDATE_KEY) != null;
  }

  public static void setSkipValidation(@NotNull PsiElement element) {
    element.putUserData(DO_NOT_VALIDATE_KEY, "");
  }

  @Override public void visitXmlAttribute(XmlAttribute attribute) {}

  private void checkAttribute(XmlAttribute attribute) {
    XmlTag tag = attribute.getParent();

    final String name = attribute.getName();
    PsiElement prevLeaf = PsiTreeUtil.prevLeaf(attribute);

    if (!(prevLeaf instanceof PsiWhiteSpace)) {
      TextRange textRange = attribute.getTextRange();
      addToResults(HighlightInfo.createHighlightInfo(
        tag instanceof HtmlTag ? HighlightInfoType.WARNING:HighlightInfoType.ERROR, 
        textRange.getStartOffset(), 
        textRange.getStartOffset(),
        XmlErrorMessages.message("attribute.should.be.preceded.with.space")
      ));
    }

    if (attribute.isNamespaceDeclaration()) {
      checkReferences(attribute.getValueElement());
      return;
    }
    final String namespace = attribute.getNamespace();

    if (XmlUtil.XML_SCHEMA_INSTANCE_URI.equals(namespace)) {
      checkReferences(attribute.getValueElement());
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
          final XmlFile xmlFile = (XmlFile)tag.getContainingFile();
          if (xmlFile != null) {
            XmlExtension.getExtension(xmlFile).createAddAttributeFix(attribute, highlightInfo);
          }
        }
      }
    }
    else {
      checkDuplicateAttribute(tag, attribute);

      if (tag instanceof HtmlTag &&
          attribute.getValueElement() == null &&
          !HtmlUtil.isSingleHtmlAttribute(name)
         ) {
        final String localizedMessage = XmlErrorMessages.message("empty.attribute.is.not.allowed", name);
        reportAttributeProblem(tag, name, attribute, localizedMessage);
      }

      // we skip resolve of attribute references since there is separate check when taking attribute descriptors
      PsiReference[] attrRefs = attribute.getReferences();
      doCheckRefs(attribute, attrRefs, attribute.getNamespacePrefix().length() > 0 ? 2 : 1);
    }
  }

  @Nullable
  private HighlightInfo reportAttributeProblem(final XmlTag tag,
                                               final String localName,
                                               final XmlAttribute attribute,
                                               final String localizedMessage) {

    final RemoveAttributeIntentionFix removeAttributeIntention = new RemoveAttributeIntentionFix(localName,attribute);

    if (!(tag instanceof HtmlTag)) {
      final HighlightInfoType tagProblemInfoType = HighlightInfoType.WRONG_REF;

      final ASTNode node = SourceTreeToPsiMap.psiElementToTree(attribute);
      assert node != null;
      final ASTNode child = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(node);
      assert child != null;
      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(
        tagProblemInfoType, child,
        localizedMessage
      );
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
                                   XmlExtension.DEFAULT_EXTENSION;
    for (XmlAttribute tagAttribute : attributes) {
      ProgressManager.checkCanceled();
      if (attribute != tagAttribute && Comparing.strEqual(attribute.getName(), tagAttribute.getName())) {
        final String localName = attribute.getLocalName();

        if (extension.canBeDuplicated(tagAttribute)) continue; // multiple import attributes are allowed in jsp directive

        HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(
          getTagProblemInfoType(tag),
          XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(SourceTreeToPsiMap.psiElementToTree(attribute)),
          XmlErrorMessages.message("duplicate.attribute", localName));
        addToResults(highlightInfo);

        IntentionAction intentionAction = new RemoveAttributeIntentionFix(localName, attribute);

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
    final PsiElement parent = value.getParent();
    if (!(parent instanceof XmlAttribute)) {
      checkReferences(value);
      return;
    }

    XmlAttribute attribute = (XmlAttribute)parent;

    XmlTag tag = attribute.getParent();

    XmlElementDescriptor elementDescriptor = tag.getDescriptor();
    XmlAttributeDescriptor attributeDescriptor = elementDescriptor != null ? elementDescriptor.getAttributeDescriptor(attribute):null;

    if (attributeDescriptor != null && value.getUserData(DO_NOT_VALIDATE_KEY) == null) {
      String error = attributeDescriptor.validateValue(value, attribute.getValue());

      if (error != null) {
        addToResults(HighlightInfo.createHighlightInfo(
            getTagProblemInfoType(tag),
            value,
            error));
        return;
      }
    }

    checkReferences(value);
  }

  private void checkReferences(PsiElement value) {
    if (value == null) return;

    doCheckRefs(value, value.getReferences());
  }

  private void doCheckRefs(final PsiElement value, final PsiReference[] references) {
    doCheckRefs(value, references, 0);
  }

  private void doCheckRefs(final PsiElement value, final PsiReference[] references, int start) {
    for (int i = start; i < references.length; ++i) {
      PsiReference reference = references[i];
      ProgressManager.checkCanceled();
      if (reference == null) {
        continue;
      }
      if(hasBadResolve(reference, false)) {
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
            else if (name.equals("href") && type.getSeverity(null) == HighlightInfoType.WARNING.getSeverity(null)) {
              continue;
            }
          }
        }
        HighlightInfo info = HighlightInfo.createHighlightInfo(
          type,
          startOffset + referenceRange.getStartOffset(),
          startOffset + referenceRange.getEndOffset(),
          description
        );
        addToResults(info);
        if (reference instanceof QuickFixProvider) ((QuickFixProvider)reference).registerQuickfix(info, reference);
        UnresolvedReferenceQuickFixProvider.registerReferenceFixes(reference, new QuickFixActionRegistrarImpl(info));
      }
    }
  }

  public static String getErrorDescription(final PsiReference reference) {
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
      description = MessageFormat.format(message, reference.getCanonicalText());
    } catch(IllegalArgumentException ex) {
      // unresolvedMessage provided by third-party reference contains wrong format string (e.g. {}), tolerate it
      description = message;
      LOG.warn(XmlErrorMessages.message("plugin.reference.message.problem",reference.getClass().getName(),message));
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
    if (myResult == null) myResult = new SmartList<HighlightInfo>();
    myResult.add(info);
  }

  public static void setDoJaxpTesting(boolean doJaxpTesting) {
    ourDoJaxpTesting = doJaxpTesting;
  }

  public void addMessage(PsiElement context, String message, int type) {
    if (message != null && message.length() > 0) {
      if (context instanceof XmlTag && XmlExtension.getExtension(context.getContainingFile()).shouldBeHighlightedAsTag((XmlTag)context)) {
        HighlightInfoType infoType = type == ERROR ? HighlightInfoType.ERROR : type == WARNING ? HighlightInfoType.WARNING : HighlightInfoType.INFO;
        addElementsForTag((XmlTag)context, message, infoType, null);
      }
      else {
        addToResults(HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF, context, message));
      }
    }
  }

  public void addMessage(final PsiElement context, final String message, final ErrorType type, final IntentionAction... fixes) {
    if (message != null && message.length() > 0) {
      final PsiFile containingFile = context.getContainingFile();
      final HighlightInfoType defaultInfoType = type == ErrorType.ERROR ? HighlightInfoType.ERROR : type == ErrorType.WARNING ? HighlightInfoType.WARNING : HighlightInfoType.INFO;

      if (context instanceof XmlTag && XmlExtension.getExtension(containingFile).shouldBeHighlightedAsTag((XmlTag)context)) {
        addElementsForTagWithManyQuickFixes((XmlTag)context, message, defaultInfoType, fixes);
      }
      else {
        final PsiElement contextOfFile = containingFile.getContext();
        final HighlightInfo highlightInfo;

        if (contextOfFile != null) {
          TextRange range = InjectedLanguageManager.getInstance(context.getProject()).injectedToHost(context, context.getTextRange());
          highlightInfo = HighlightInfo.createHighlightInfo(defaultInfoType, range, message);
        }
        else {
          highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF, context, message);
        }

        if (fixes != null) {
          for (final IntentionAction quickFixAction : fixes) {
            if (quickFixAction == null) continue;
            QuickFixAction.registerQuickFixAction(highlightInfo, quickFixAction);
          }
        }
        addToResults(highlightInfo);
      }
    }
  }

  public static void visitJspElement(OuterLanguageElement text) {
    PsiElement parent = text.getParent();

    if (parent instanceof XmlText) {
      parent = parent.getParent();
    }

    parent.putUserData(DO_NOT_VALIDATE_KEY, "");
  }

  public boolean suitableForFile(final PsiFile file) {
    return file instanceof XmlFile;
  }

  public void visit(final PsiElement element, final HighlightInfoHolder holder) {
    element.accept(this);

    List<HighlightInfo> result = myResult;
    holder.addAll(result);
    myResult = null;
  }

  public boolean analyze(Runnable action, final boolean updateWholeFile, final PsiFile file) {
    try {
      action.run();
    }
    finally {
      myResult = null;
    }
    return true;
  }

  public HighlightVisitor clone() {
    return new XmlHighlightVisitor();
  }

  public int order() {
    return 1;
  }

  public static String getUnquotedValue(XmlAttributeValue value, XmlTag tag) {
    String unquotedValue = StringUtil.stripQuotesAroundValue(value.getText());

    if (tag instanceof HtmlTag) {
      unquotedValue = unquotedValue.toLowerCase();
    }

    return unquotedValue;
  }
}
