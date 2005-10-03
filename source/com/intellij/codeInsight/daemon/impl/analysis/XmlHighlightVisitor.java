package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.*;
import com.intellij.codeInsight.daemon.impl.EditInspectionToolsSettingsAction;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.RefCountHolder;
import com.intellij.codeInsight.daemon.impl.quickfix.AddHtmlTagOrAttributeToCustoms;
import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.IgnoreExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.jsp.impl.JspElementDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.JspManager;
import com.intellij.psi.impl.source.jsp.jspJava.JspDirective;
import com.intellij.psi.impl.source.jsp.jspJava.JspText;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * @author Mike
 */
public class XmlHighlightVisitor extends PsiElementVisitor implements Validator.ValidationHost {
  public static final Key<String> DO_NOT_VALIDATE_KEY = Key.create("do not validate");
  private List<HighlightInfo> myResult;
  private RefCountHolder myRefCountHolder;
  private DaemonCodeAnalyzerSettings mySettings;

  private static boolean ourDoJaxpTesting;

  private static final @NonNls String AMP_ENTITY = "&amp;";
  private static final @NonNls String TAGLIB_DIRECTIVE = "taglib";
  private static final @NonNls String URI_ATT = "uri";
  private static final @NonNls String TAGDIR_ATT = "tagdir";
  private static final @NonNls String ID_ATT = "id";
  private static final @NonNls String LOCATION_ATT_SUFFIX = "Location";

  public XmlHighlightVisitor(DaemonCodeAnalyzerSettings settings) {
    mySettings = settings;
  }

  public void setRefCountHolder(RefCountHolder refCountHolder) {
    myRefCountHolder = refCountHolder;
  }

  public List<HighlightInfo> getResult() {
    return myResult;
  }

  public synchronized void clearResult() {
    myResult = null;
  }

  private void addElementsForTag(XmlTag tag,
                                 String localizedMessage,
                                 HighlightInfoType type,
                                 IntentionAction quickFixAction) {
    addElementsForTagWithManyQuickFixes(tag, localizedMessage, type, quickFixAction);
  }
  private void addElementsForTagWithManyQuickFixes(XmlTag tag,
                                                   String localizedMessage,
                                                   HighlightInfoType type,
                                                   IntentionAction... quickFixActions) {
    ASTNode tagElement = SourceTreeToPsiMap.psiElementToTree(tag);
    ASTNode childByRole = XmlChildRole.START_TAG_NAME_FINDER.findChild(tagElement);

    if(childByRole != null) {
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(type, childByRole, localizedMessage);
      addToResults(highlightInfo);

      for (final IntentionAction quickFixAction : quickFixActions) {
        if (quickFixAction == null) continue;
        QuickFixAction.registerQuickFixAction(highlightInfo, quickFixAction, null);
      }
    }

    childByRole = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(tagElement);

    if(childByRole != null) {
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(type, childByRole, localizedMessage);
      for (final IntentionAction quickFixAction : quickFixActions) {
        if (quickFixAction == null) continue;
        QuickFixAction.registerQuickFixAction(highlightInfo, quickFixAction, null);
      }

      addToResults(highlightInfo);
    }
  }

  public void visitXmlToken(XmlToken token) {
    if (token.getTokenType() == XmlTokenType.XML_NAME) {
      PsiElement element = token.getPrevSibling();
      while(element instanceof PsiWhiteSpace) element = element.getPrevSibling();

      if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_START_TAG_START) {
        PsiElement parent = element.getParent();

        if (parent instanceof XmlTag && !(token.getNextSibling() instanceof JspText)) {
          XmlTag tag = (XmlTag)parent;
          checkTag(tag);
        }
      }
    }
  }

  //public void visitXmlText(XmlText text) {
  //  final String textString = text.getText();
  //  int ampInd = textString.indexOf('&');
  //  if (ampInd!=-1) {
  //
  //  }
  //  super.visitXmlText(text);
  //}


  private void checkTag(XmlTag tag) {
    if (ourDoJaxpTesting) return;

    if (tag.getName() == null) {
      return;
    }

    if (!checkTagIsClosed(tag)) return;

    if (!(tag.getParent() instanceof XmlTag)) {
      checkRootTag(tag);
    }

    if (myResult == null) {
      checkTagByDescriptor(tag);
    }

    if (myResult == null) {
      if (tag.getUserData(DO_NOT_VALIDATE_KEY) == null) {
        if (tag instanceof HtmlTag && tag.getDescriptor() instanceof AnyXmlElementDescriptor) {
          final String name = tag.getName();

          reportOneTagProblem(
            tag,
            name,
            XmlErrorMessages.message("unknown.html.tag", name),
            null,
            mySettings.getInspectionProfile(tag).getAdditionalHtmlTags(),
            HighlightInfoType.CUSTOM_HTML_TAG,
            HighlightDisplayKey.CUSTOM_HTML_TAG
          );

          return;
        }

        checkReferences(tag, QuickFixProvider.NULL);
      }
    }
  }

  public void registerXmlErrorQuickFix(final PsiErrorElement element, final HighlightInfo highlightInfo) {
    final String text = element.getErrorDescription();
    if (text != null && text.startsWith(XmlErrorMessages.message("unescaped.ampersand"))) {
      QuickFixAction.registerQuickFixAction(highlightInfo, new IntentionAction() {
        public String getText() {
          return XmlErrorMessages.message("escape.ampersand.quickfix");
        }

        public String getFamilyName() {
          return getText();
        }

        public boolean isAvailable(Project project, Editor editor, PsiFile file) {
          return true;
        }

        public void invoke(Project project, Editor editor, PsiFile file) {
          if (!CodeInsightUtil.prepareFileForWrite(file)) return;
          final int textOffset = element.getTextOffset();
          editor.getDocument().replaceString(textOffset,textOffset + 1,AMP_ENTITY);
        }

        public boolean startInWriteAction() {
          return true;
        }
      }, null);
    }
  }

  static class RenameTagBeginOrEndIntentionAction implements IntentionAction {
    private boolean myStart;
    private XmlTag myTagToChange;
    private String myName;

    RenameTagBeginOrEndIntentionAction(XmlTag tagToChange, String name, boolean start) {
      myStart = start;
      myTagToChange = tagToChange;
      myName = name;
    }

    public String getText() {
      return myStart ? XmlErrorMessages.message("rename.start.tag.name.intention") : XmlErrorMessages.message("rename.end.tag.name.intention");
    }

    public String getFamilyName() {
      return getText();
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
      return true;
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      if (!CodeInsightUtil.prepareFileForWrite(file)) return;
      myTagToChange.setName(myName);
    }

    public boolean startInWriteAction() {
      return true;
    }
  }

  private boolean checkTagIsClosed(XmlTag tag) {
    PsiElement[] children = tag.getChildren();
    String name = tag.getName();

    boolean insideEndTag = false;
    XmlToken startTagNameToken = null;

    for (PsiElement child : children) {
      if (child instanceof XmlToken) {
        final XmlToken xmlToken = (XmlToken)child;
        if (xmlToken.getTokenType() == XmlTokenType.XML_EMPTY_ELEMENT_END) return true;
        if (xmlToken.getTokenType() == XmlTokenType.XML_END_TAG_START) {
          insideEndTag = true;
        }

        if (xmlToken.getTokenType() == XmlTokenType.XML_NAME) {
          if (insideEndTag) {
            String text = xmlToken.getText();
            if (tag instanceof HtmlTag) {
              text = text.toLowerCase();
              name = name.toLowerCase();
            }

            boolean isExtraHtmlTagEnd = false;
            if (text.equals(name)) {
              isExtraHtmlTagEnd = tag instanceof HtmlTag && HtmlUtil.isSingleHtmlTag(name);
              if (!isExtraHtmlTagEnd) return true;
            }

            HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(
              isExtraHtmlTagEnd ? HighlightInfoType.WARNING : HighlightInfoType.ERROR,
              xmlToken,
              isExtraHtmlTagEnd ? XmlErrorMessages.message("extra.closing.tag.for.empty.element") : XmlErrorMessages.message("wrong.closing.tag.name"));
            addToResults(highlightInfo);

            if (isExtraHtmlTagEnd) {
              QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveExtraClosingTagIntentionAction(xmlToken), null);
            } else {
              IntentionAction intentionAction = new RenameTagBeginOrEndIntentionAction(tag, name, false);
              IntentionAction intentionAction2 = new RenameTagBeginOrEndIntentionAction(tag, text, true);

              QuickFixAction.registerQuickFixAction(highlightInfo, intentionAction, null);
              QuickFixAction.registerQuickFixAction(highlightInfo, startTagNameToken.getTextRange(), intentionAction2, null);
            }

            return false;
          }
          else {
            startTagNameToken = xmlToken;
          }
        }
      }
    }

    return tag instanceof HtmlTag &&
           (HtmlUtil.isOptionalEndForHtmlTag(name) ||
            HtmlUtil.isSingleHtmlTag(name)
           );
  }

  private void checkTagByDescriptor(final XmlTag tag) {
    String name = tag.getName();

    if (tag instanceof JspDirective) {
      checkDirective(name, tag);
    }

    XmlElementDescriptor elementDescriptor = null;

    final PsiElement parent = tag.getParent();
    if (parent instanceof XmlTag) {
      XmlTag parentTag = (XmlTag)parent;
      final XmlElementDescriptor parentDescriptor = parentTag.getDescriptor();

      if (parentDescriptor != null) {
        elementDescriptor = parentDescriptor.getElementDescriptor(tag);
      }

      if (parentDescriptor != null &&
          elementDescriptor == null &&
          parentTag.getUserData(DO_NOT_VALIDATE_KEY) == null
         ) {
        addElementsForTag(
          tag,
          XmlErrorMessages.message("element.is.not.allowed.here", name),
          getTagProblemInfoType(tag),
          null
        );
        return;
      }

      if (elementDescriptor instanceof AnyXmlElementDescriptor || parentDescriptor == null) {
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

    XmlAttributeDescriptor[] attributeDescriptors = elementDescriptor.getAttributesDescriptors();
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
        if (tag.getAttribute(attrName, tag.getNamespace()) == null) {
          if (!(elementDescriptor instanceof JspElementDescriptor) ||
              !((JspElementDescriptor)elementDescriptor).isRequiredAttributeImplicitlyPresent(tag, attrName)
              ) {
            final InsertRequiredAttributeIntention insertRequiredAttributeIntention = new InsertRequiredAttributeIntention(
                tag, attrName, null);
            final String localizedMessage = XmlErrorMessages.message("element.doesnt.have.required.attribute", name, attrName);

            reportOneTagProblem(
              tag,
              attrName,
              localizedMessage,
              insertRequiredAttributeIntention,
              mySettings.getInspectionProfile(tag).getAdditionalNotRequiredHtmlAttributes(),
              HighlightInfoType.REQUIRED_HTML_ATTRIBUTE,
              HighlightDisplayKey.REQUIRED_HTML_ATTRIBUTE
            );
          }
        }
      }
    }

    if (elementDescriptor instanceof Validator) {
      ((Validator)elementDescriptor).validate(tag,this);
    }
  }

  private void reportOneTagProblem(final XmlTag tag,
                                   final String name,
                                   final String localizedMessage,
                                   final IntentionAction basicIntention,
                                   String additional,
                                   HighlightInfoType type,
                                   HighlightDisplayKey key) {
    boolean htmlTag = false;

    if (tag instanceof HtmlTag) {
      htmlTag = true;
      if(isAdditionallyDeclared(additional, name)) return;
    }

    if (htmlTag && mySettings.getInspectionProfile(tag).isToolEnabled(key)) {
      addElementsForTagWithManyQuickFixes(
        tag,
        localizedMessage,
        type,
        new IntentionAction[] {
          new AddHtmlTagOrAttributeToCustoms(name,type),
          new EditInspectionToolsSettingsAction(key),
          basicIntention
        }
      );
    } else if (!htmlTag) {
      addElementsForTag(
        tag,
        localizedMessage,
        HighlightInfoType.WRONG_REF,
        basicIntention
      );
    }
  }

  private static boolean isAdditionallyDeclared(final String additional, final String name) {
    StringTokenizer tokenizer = new StringTokenizer(additional, ", ");
    while (tokenizer.hasMoreTokens()) {
      if (name.equals(tokenizer.nextToken())) {
        return true;
      }
    }

    return false;
  }

  private void checkDirective(final String name, final XmlTag tag) {
    if (TAGLIB_DIRECTIVE.equals(name)) {
      final String uri = tag.getAttributeValue(URI_ATT);

      if (uri == null) {
        if (tag.getAttributeValue(TAGDIR_ATT) == null) {
          final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(
            HighlightInfoType.WRONG_REF,
            XmlChildRole.START_TAG_NAME_FINDER.findChild(SourceTreeToPsiMap.psiElementToTree(tag)),
            XmlErrorMessages.message("either.uri.or.tagdir.attribute.should.be.specified")
          );

          addToResults(highlightInfo);
          final JspFile jspFile = (JspFile)tag.getContainingFile();
          QuickFixAction.registerQuickFixAction(
            highlightInfo,
            new InsertRequiredAttributeIntention(
              tag,
              URI_ATT,
              JspManager.getInstance(jspFile.getProject()).getPossibleTldUris(jspFile)
            ),
            null
          );

          QuickFixAction.registerQuickFixAction(
            highlightInfo,
            new InsertRequiredAttributeIntention(tag, TAGDIR_ATT,null),
            null
          );
        }
      }
    }
  }

  private static HighlightInfoType getTagProblemInfoType(XmlTag tag) {
    return tag instanceof HtmlTag && XmlUtil.HTML_URI.equals(tag.getNamespace()) ? HighlightInfoType.WARNING : HighlightInfoType.WRONG_REF;
  }

  private void checkRootTag(XmlTag tag) {
    XmlFile file = (XmlFile)tag.getContainingFile();

    XmlProlog prolog = file.getDocument().getProlog();

    if (prolog == null) {
      return;
    }

    XmlDoctype doctype = prolog.getDoctype();

    if (doctype == null) {
      return;
    }

    XmlElement nameElement = doctype.getNameElement();

    if (nameElement == null) {
      return;
    }

    String name = tag.getName();
    String text = nameElement.getText();
    if (tag instanceof HtmlTag) {
      name = name.toLowerCase();
      text = text.toLowerCase();
    }
    if (!name.equals(text)) {
      addElementsForTag(tag, XmlErrorMessages.message("wrong.root.element"), HighlightInfoType.WRONG_REF, null);
    }
  }

  public void visitXmlAttribute(XmlAttribute attribute) {
    XmlTag tag = attribute.getParent();

    if (attribute.isNamespaceDeclaration()) {
      checkNamespaceAttribute(attribute);
      return;
    } else {
      final String namespace = attribute.getNamespace();

      if (XmlUtil.XML_SCHEMA_INSTANCE_URI.equals(namespace)) {
        if (attribute.getName().endsWith(LOCATION_ATT_SUFFIX)) {
          checkSchemaLocationAttribute(attribute);
        } else {
          if(attribute.getValueElement() != null) {
            checkReferences(attribute.getValueElement(), QuickFixProvider.NULL);
          }
        }
        return;
      }
    }

    XmlElementDescriptor elementDescriptor = tag.getDescriptor();
    if (elementDescriptor == null || ourDoJaxpTesting) return;
    XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor(attribute);

    String name = attribute.getName();

    if (attributeDescriptor == null) {
      final String localizedMessage = XmlErrorMessages.message("attribute.is.not.allowed.here", name);
      reportAttributeProblem(tag, name, attribute, localizedMessage);
    }
    else {
      checkDuplicateAttribute(tag, attribute);

      if (tag instanceof HtmlTag &&
          attribute.getValueElement() == null &&
          !HtmlUtil.isSingleHtmlAttribute(attribute.getName())
         ) {
        final String localizedMessage = XmlErrorMessages.message("empty.attribute.is.not.allowed", name);
        reportAttributeProblem(tag, name, attribute, localizedMessage);
      }
    }
  }

  private void reportAttributeProblem(final XmlTag tag,
                                      final String localName,
                                      final XmlAttribute attribute,
                                      final String localizedMessage) {
    final HighlightInfoType tagProblemInfoType;
    IntentionAction[] quickFixes;

    if (tag instanceof HtmlTag) {
      final InspectionProfileImpl inspectionProfile = mySettings.getInspectionProfile(tag);
      if(isAdditionallyDeclared(inspectionProfile.getAdditionalHtmlAttributes(),localName)) return;
      if (!inspectionProfile.isToolEnabled(HighlightDisplayKey.CUSTOM_HTML_ATTRIBUTE)) return;
      tagProblemInfoType = HighlightInfoType.CUSTOM_HTML_ATTRIBUTE;

      quickFixes = new IntentionAction[] {
        new AddHtmlTagOrAttributeToCustoms(localName,tagProblemInfoType),
        new EditInspectionToolsSettingsAction(HighlightDisplayKey.CUSTOM_HTML_ATTRIBUTE)
      };
    } else {
      tagProblemInfoType = HighlightInfoType.WRONG_REF; quickFixes = null;
    }

    final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(
      tagProblemInfoType,
      XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(SourceTreeToPsiMap.psiElementToTree(attribute)),
      localizedMessage
    );
    addToResults(highlightInfo);

    if (quickFixes != null) {
      for (IntentionAction quickFix : quickFixes) {
        QuickFixAction.registerQuickFixAction(highlightInfo, quickFix, null);
      }
    }
  }

  private void checkDuplicateAttribute(XmlTag tag, final XmlAttribute attribute) {
    if (tag.getUserData(DO_NOT_VALIDATE_KEY) != null) {
      return;
    }
    XmlAttribute[] attributes = tag.getAttributes();

    for (XmlAttribute tagAttribute : attributes) {
      if (attribute != tagAttribute && Comparing.strEqual(attribute.getName(), tagAttribute.getName())) {
        final String localName = attribute.getLocalName();
        HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(
          getTagProblemInfoType(tag),
          XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(SourceTreeToPsiMap.psiElementToTree(attribute)),
          XmlErrorMessages.message("duplicate.attribute", localName));
        addToResults(highlightInfo);

        IntentionAction intentionAction = new RemoveDuplicatedAttributeIntentionAction(localName, attribute);

        QuickFixAction.registerQuickFixAction(highlightInfo, intentionAction, null);
      }
    }
  }

  public void visitXmlTag(XmlTag tag) {
  }

  public void visitXmlAttributeValue(XmlAttributeValue value) {
    if (!(value.getParent() instanceof XmlAttribute)) {
      checkReferences(value, QuickFixProvider.NULL);
      return;
    }

    XmlAttribute attribute = (XmlAttribute)value.getParent();
    if (value.getUserData(DO_NOT_VALIDATE_KEY) != null) {
      return;
    }

    XmlTag tag = attribute.getParent();

    XmlElementDescriptor elementDescriptor = tag.getDescriptor();
    if (elementDescriptor == null) return;
    XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor(attribute);
    if (attributeDescriptor == null) return;

    String error = attributeDescriptor.validateValue(value, attribute.getValue());

    if (error != null) {
      addToResults(HighlightInfo.createHighlightInfo(
          getTagProblemInfoType(tag),
          value,
          error));
      return;
    }

    final RefCountHolder refCountHolder = myRefCountHolder;  // To make sure it doesn't get null in multi-threaded envir.
    if (refCountHolder != null &&
        attributeDescriptor.hasIdType() &&
        tag.getParent().getUserData(DO_NOT_VALIDATE_KEY) == null
      ) {
      final String unquotedValue = getUnquotedValue(value, tag);

      if (XmlUtil.isSimpleXmlAttributeValue(unquotedValue)) {
        XmlTag xmlTag = refCountHolder.getTagById(unquotedValue);

        if (xmlTag == null ||
            !xmlTag.isValid() ||
            xmlTag == tag
           ) {
          refCountHolder.registerTagWithId(unquotedValue,tag);
        } else {
          XmlAttribute anotherTagIdValue = xmlTag.getAttribute(ID_ATT, null);

          if (anotherTagIdValue!=null &&
              getUnquotedValue(anotherTagIdValue.getValueElement(), xmlTag).equals(unquotedValue)
             ) {
            addToResults(HighlightInfo.createHighlightInfo(
              HighlightInfoType.WRONG_REF,
              value,
              XmlErrorMessages.message("duplicate.id.reference")));
            addToResults(HighlightInfo.createHighlightInfo(
              HighlightInfoType.WRONG_REF,
              xmlTag.getAttribute(ID_ATT,null).getValueElement(),
              XmlErrorMessages.message("duplicate.id.reference")));
            return;
          } else {
            // tag previously has that id
            refCountHolder.registerTagWithId(unquotedValue,tag);
          }
        }
      }
    }

    QuickFixProvider quickFixProvider = QuickFixProvider.NULL;
    if (attributeDescriptor instanceof QuickFixProvider) quickFixProvider = (QuickFixProvider)attributeDescriptor;

    checkReferences(value, quickFixProvider);

  }

  public static HighlightInfo checkIdRefAttrValue(XmlAttributeValue value, RefCountHolder holder) {
    if (!(value.getParent() instanceof XmlAttribute) || holder==null) return null;
    XmlAttribute attribute = (XmlAttribute)value.getParent();

    XmlTag tag = attribute.getParent();

    XmlElementDescriptor elementDescriptor = tag.getDescriptor();
    if (elementDescriptor == null) return null;
    XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor(attribute);
    if (attributeDescriptor == null) return null;

    if (attributeDescriptor.hasIdRefType()) {
      String unquotedValue = getUnquotedValue(value, tag);
      if (XmlUtil.isSimpleXmlAttributeValue(unquotedValue)) {
        XmlTag xmlTag = holder.getTagById(unquotedValue);

        if (xmlTag == null || !xmlTag.isValid()) {
          return HighlightInfo.createHighlightInfo(
            HighlightInfoType.WRONG_REF,
            value,
            XmlErrorMessages.message("invalid.id.reference")
          );
        }
      }
    }

    return null;
  }

  private static String getUnquotedValue(XmlAttributeValue value, XmlTag tag) {
    String unquotedValue = StringUtil.stripQuotesAroundValue(value.getText());

    if (tag instanceof HtmlTag) {
      unquotedValue = unquotedValue.toLowerCase();
    }

    return unquotedValue;
  }

  private void checkReferences(PsiElement value, QuickFixProvider quickFixProvider) {
    PsiReference[] references = value.getReferences();

    for (final PsiReference reference : references) {
      if (reference != null) {
        if (!reference.isSoft()) {
          boolean hasBadResolve;

          if (reference instanceof PsiPolyVariantReference) {
            hasBadResolve = ((PsiPolyVariantReference)reference).multiResolve(false).length == 0;
          } else {
            hasBadResolve = reference.resolve() == null;
          }

          if(hasBadResolve) {
            String message;
            if (reference instanceof GenericReference) {
              message = ((GenericReference)reference).getUnresolvedMessage();
            }
            else {
              message = XmlErrorMessages.message("cannot.resolve.symbol");
            }

            HighlightInfo info = HighlightInfo.createHighlightInfo(
              getTagProblemInfoType(PsiTreeUtil.getParentOfType(value, XmlTag.class)),
              reference.getElement().getTextRange().getStartOffset() + reference.getRangeInElement().getStartOffset(),
              reference.getElement().getTextRange().getStartOffset() + reference.getRangeInElement().getEndOffset(),
              MessageFormat.format(message, reference.getCanonicalText()));
            addToResults(info);
            quickFixProvider.registerQuickfix(info, reference);
            if (reference instanceof QuickFixProvider) ((QuickFixProvider)reference).registerQuickfix(info, reference);
          }
        }
        if(reference instanceof PsiJavaReference && myRefCountHolder != null){
          final PsiJavaReference psiJavaReference = (PsiJavaReference)reference;
          myRefCountHolder.registerReference(psiJavaReference, psiJavaReference.advancedResolve(false));
        }
      }
    }
  }

  public void visitXmlDoctype(XmlDoctype xmlDoctype) {
    String uri = xmlDoctype.getDtdUri();
    if (uri == null || ExternalResourceManagerEx.getInstanceEx().isIgnoredResource(uri)) return;

    XmlFile xmlFile = XmlUtil.findXmlFile(xmlDoctype.getContainingFile(), uri);
    if (xmlFile == null) {
      HighlightInfo info = HighlightInfo.createHighlightInfo(
            HighlightInfoType.WRONG_REF,
            xmlDoctype.getDtdUrlElement().getTextRange().getStartOffset() + 1,
            xmlDoctype.getDtdUrlElement().getTextRange().getEndOffset() - 1,
            XmlErrorMessages.message("uri.is.not.registered"));
      addToResults(info);
      QuickFixAction.registerQuickFixAction(info, new FetchExtResourceAction(), null);
      QuickFixAction.registerQuickFixAction(info, new IgnoreExtResourceAction(), null);
    }
  }

  private synchronized void addToResults(final HighlightInfo info) {
    if (myResult == null) myResult = new SmartList<HighlightInfo>();
    myResult.add(info);
  }

  public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitExpression(expression);
  }

  private void checkNamespaceAttribute(XmlAttribute attribute) {
    String namespace = null;

    if (attribute.isNamespaceDeclaration()) {
      namespace = attribute.getValue();
    }

    if(namespace == null || namespace.length() < 1|| ExternalResourceManagerEx.getInstanceEx().isIgnoredResource(namespace)) return;
    XmlTag declarationTag = attribute.getParent();

    if(declarationTag.getNSDescriptor(namespace, true) != null) return;

    String attributeValue = declarationTag.getAttributeValue("targetNamespace");
    if (attributeValue != null && attributeValue.equals(namespace)) {
      // we referencing ns while defining it
      return;
    }

    XmlAttributeValue element = attribute.getValueElement();
    if(element == null) return;
    int start = element.getTextRange().getStartOffset() + 1;
    int end = element.getTextRange().getEndOffset() - 1;

    reportURIProblem(start,end);
  }

  private void checkSchemaLocationAttribute(XmlAttribute attribute) {
    if(attribute.getValueElement() == null) return;
    String location = attribute.getValue();

    if (attribute.getLocalName().equals(XmlUtil.NO_NAMESPACE_SCHEMA_LOCATION_ATT)) {
      if(ExternalResourceManagerEx.getInstanceEx().isIgnoredResource(location)) return;

      if(XmlUtil.findXmlFile(attribute.getContainingFile(),location) == null) {
        int start = attribute.getValueElement().getTextOffset();
        reportURIProblem(start,start + location.length());
      }
    } else if (attribute.getLocalName().equals(XmlUtil.SCHEMA_LOCATION_ATT)) {
      StringTokenizer tokenizer = new StringTokenizer(location);
      XmlFile file = null;
      final ExternalResourceManagerEx externalResourceManager = ExternalResourceManagerEx.getInstanceEx();

      while(tokenizer.hasMoreElements()) {
        final String namespace = tokenizer.nextToken(); // skip namespace
        if (!tokenizer.hasMoreElements()) return;
        String url = tokenizer.nextToken();

        if(externalResourceManager.isIgnoredResource(url)) continue;
        if (file == null) {
          file = (XmlFile)attribute.getContainingFile();
        }

        if(XmlUtil.findXmlFile(file,url) == null &&
           externalResourceManager.getResourceLocation(namespace).equals(namespace)
          ) {
          int start = attribute.getValueElement().getTextOffset() + location.indexOf(url);
          reportURIProblem(start,start+url.length());
        }
      }
    }
  }

  private void reportURIProblem(int start, int end) { // report the problem
    if (start > end) {
      end = start;
    }
    HighlightInfo info = HighlightInfo.createHighlightInfo(
      HighlightInfoType.WRONG_REF,
      start,
      end,
      XmlErrorMessages.message("uri.is.not.registered"));
    QuickFixAction.registerQuickFixAction(info, new FetchExtResourceAction(), null);
    QuickFixAction.registerQuickFixAction(info, new IgnoreExtResourceAction(), null);
    addToResults(info);
  }

  public static void setDoJaxpTesting(boolean doJaxpTesting) {
    ourDoJaxpTesting = doJaxpTesting;
  }

  public void addMessage(PsiElement context, String message, int type) {
    if (message != null && message.length() > 0) {
      if (context instanceof XmlTag) {
        addElementsForTag((XmlTag)context, message, type == ERROR ? HighlightInfoType.ERROR : HighlightInfoType.WARNING, null);
      }
      else {
        addToResults(HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF, context, message));
      }
    }
  }

  public void visitJspElement(JspText text) {
    PsiElement parent = text.getParent();

    if (parent instanceof XmlText) {
      parent = parent.getParent();
    }

    parent.putUserData(DO_NOT_VALIDATE_KEY, "");
  }

  private static class InsertRequiredAttributeIntention implements IntentionAction {
    private final XmlTag myTag;
    private final String myAttrName;
    private String[] myValues;
    @NonNls
    private static final String NAME_TEMPLATE_VARIABLE = "name";

    public InsertRequiredAttributeIntention(final XmlTag tag, final String attrName,final String[] values) {
      myTag = tag;
      myAttrName = attrName;
      myValues = values;
    }

    public String getText() {
      return XmlErrorMessages.message("insert.required.attribute.quickfix.text", myAttrName);
    }

    public String getFamilyName() {
      return XmlErrorMessages.message("insert.required.attribute.quickfix.family");
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
      return true;
    }

    public void invoke(final Project project, final Editor editor, PsiFile file) {
      if (!CodeInsightUtil.prepareFileForWrite(file)) return;
      ASTNode treeElement = SourceTreeToPsiMap.psiElementToTree(myTag);
      PsiElement anchor = SourceTreeToPsiMap.treeElementToPsi(
        XmlChildRole.EMPTY_TAG_END_FINDER.findChild(treeElement)
      );

      if (anchor == null) {
        anchor = SourceTreeToPsiMap.treeElementToPsi(
          XmlChildRole.START_TAG_END_FINDER.findChild(treeElement)
        );
      }

      if (anchor == null) return;

      final Template template = TemplateManager.getInstance(project).createTemplate("", "");
      template.addTextSegment(" " + myAttrName + "=\"");

      Expression expression = new Expression() {
        TextResult result = new TextResult("");

        public Result calculateResult(ExpressionContext context) {
          return result;
        }

        public Result calculateQuickResult(ExpressionContext context) {
          return null;
        }

        public LookupItem[] calculateLookupItems(ExpressionContext context) {
          final LookupItem items[] = new LookupItem[myValues == null ? 0 : myValues.length];

          if (myValues != null) {
            for (int i = 0; i < items.length; i++) {
              items[i] = LookupItemUtil.objectToLookupItem(myValues[i]);
            }
          }
          return items;
        }
      };
      template.addVariable(NAME_TEMPLATE_VARIABLE, expression, expression, true);
      template.addTextSegment("\"");

      final PsiElement anchor1 = anchor;

      final Runnable runnable = new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
              public void run() {
                int textOffset = anchor1.getTextOffset();
                editor.getCaretModel().moveToOffset(textOffset);
                TemplateManager.getInstance(project).startTemplate(editor, template, null);
              }
            }
          );
        }
      };

      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        Runnable commandRunnable = new Runnable() {
          public void run() {
            CommandProcessor.getInstance().executeCommand(
              project,
              runnable,
              getText(),
              getFamilyName()
            );
          }
        };

        ApplicationManager.getApplication().invokeLater(commandRunnable);
      }
      else {
        runnable.run();
      }
    }

    public boolean startInWriteAction() {
      return true;
    }
  }

  private static class RemoveDuplicatedAttributeIntentionAction implements IntentionAction {
    private final String myLocalName;
    private final XmlAttribute myAttribute;

    public RemoveDuplicatedAttributeIntentionAction(final String localName, final XmlAttribute attribute) {
      myLocalName = localName;
      myAttribute = attribute;
    }

    public String getText() {
      return XmlErrorMessages.message("remove.duplicated.attribute.quickfix.text", myLocalName);
    }

    public String getFamilyName() {
      return XmlErrorMessages.message("remove.duplicated.attribute.quickfix.family");
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
      return true;
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      if (!CodeInsightUtil.prepareFileForWrite(file)) return;
      PsiElement next = findNextAttribute(myAttribute);
      myAttribute.delete();

      if (next != null) {
        editor.getCaretModel().moveToOffset(next.getTextRange().getStartOffset());
      }
    }

    private static PsiElement findNextAttribute(final XmlAttribute attribute) {
      PsiElement nextSibling = attribute.getNextSibling();
      while (nextSibling != null) {
        if (nextSibling instanceof XmlAttribute) return nextSibling;
        nextSibling =  nextSibling.getNextSibling();
      }
      return null;
    }

    public boolean startInWriteAction() {
      return true;
    }
  }

  private static class RemoveExtraClosingTagIntentionAction implements IntentionAction {
    private final XmlToken myXmlToken;

    public RemoveExtraClosingTagIntentionAction(final XmlToken xmlToken) {
      myXmlToken = xmlToken;
    }

    public String getText() {
      return XmlErrorMessages.message("remove.extra.closing.tag.quickfix");
    }

    public String getFamilyName() {
      return XmlErrorMessages.message("remove.extra.closing.tag.quickfix");
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
      return true;
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      if (!CodeInsightUtil.prepareFileForWrite(file)) return;

      XmlToken tagEndStart = myXmlToken;
      while(tagEndStart.getTokenType() != XmlTokenType.XML_END_TAG_START) {
        final PsiElement prevSibling = tagEndStart.getPrevSibling();
        if (!(prevSibling instanceof XmlToken)) break;
        tagEndStart = (XmlToken)prevSibling;
      }

      final PsiElement parent = tagEndStart.getParent();
      parent.deleteChildRange(tagEndStart,parent.getLastChild());
    }

    public boolean startInWriteAction() {
      return true;
    }
  }
}
