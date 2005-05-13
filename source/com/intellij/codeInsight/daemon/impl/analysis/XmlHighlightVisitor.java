package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.Validator;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.RefCountHolder;
import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.IgnoreExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * @author Mike
 */
public class XmlHighlightVisitor extends PsiElementVisitor implements Validator.ValidationHost {
  private static final String UNKNOWN_SYMBOL = "Cannot resolve symbol {0}";
  private List<HighlightInfo> myResult = new SmartList<HighlightInfo>();
  private RefCountHolder myRefCountHolder;

  private static boolean ourDoJaxpTesting;

  public XmlHighlightVisitor() {
  }

  public void setRefCountHolder(RefCountHolder refCountHolder) {
    myRefCountHolder = refCountHolder;
  }

  public List<HighlightInfo> getResult() {
    return myResult;
  }

  public void clearResult() {
    myResult.clear();
    myRefCountHolder = null;
  }

  private static void addElementsForTag(XmlTag tag,
                                 String localizedMessage,
                                 List<HighlightInfo> result,
                                 HighlightInfoType type,
                                 IntentionAction quickFixAction) {
    ASTNode tagElement = SourceTreeToPsiMap.psiElementToTree(tag);
    ASTNode childByRole = XmlChildRole.START_TAG_NAME_FINDER.findChild(tagElement);

    if(childByRole != null) {
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(type, childByRole, localizedMessage);
      result.add(highlightInfo);
      QuickFixAction.registerQuickFixAction(highlightInfo, quickFixAction, null);
    }

    childByRole = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(tagElement);

    if(childByRole != null) {
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(type, childByRole, localizedMessage);
      QuickFixAction.registerQuickFixAction(highlightInfo, quickFixAction, null);
      result.add(highlightInfo);
    }
  }

  public void visitXmlToken(XmlToken token) {
    if (token.getTokenType() == XmlTokenType.XML_NAME) {
      PsiElement element = token.getPrevSibling();

      if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_START_TAG_START) {
        PsiElement parent = element.getParent();

        if (parent instanceof XmlTag) {
          XmlTag tag = (XmlTag)parent;
          checkTag(tag);
        }
      }
    }
  }

  public void visitXmlDocument(XmlDocument document) {
    XmlTag rootTag = document.getRootTag();
    XmlNSDescriptor nsDescriptor = rootTag == null ? null : rootTag.getNSDescriptor(rootTag.getNamespace(), false);

    if (nsDescriptor instanceof Validator) {
      ((Validator)nsDescriptor).validate(document, this);
    }
  }

  private void checkTag(XmlTag tag) {
    if (ourDoJaxpTesting) return;

    if (tag.getName() == null) {
      return;
    }

    if (!checkTagIsClosed(tag)) return;

    if (!(tag.getParent() instanceof XmlTag)) {
      checkRootTag(tag);
    }

    if (myResult.isEmpty()) {
      checkTagByDescriptor(tag);
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
      return "Rename " + ((myStart)?"Start":"End") + " Tag Name";
    }

    public String getFamilyName() {
      return getText();
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
      return true;
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
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
        XmlToken xmlToken = (XmlToken)child;
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

            if (text.equals(name)) return true;

            HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(
              HighlightInfoType.ERROR,
              xmlToken,
              "Wrong closing tag name");
            myResult.add(highlightInfo);
            IntentionAction intentionAction = new RenameTagBeginOrEndIntentionAction(tag, name, false);
            IntentionAction intentionAction2 = new RenameTagBeginOrEndIntentionAction(tag, text, true);

            QuickFixAction.registerQuickFixAction(highlightInfo, intentionAction, null);
            QuickFixAction.registerQuickFixAction(highlightInfo, startTagNameToken.getTextRange(), intentionAction2, null);

            return false;
          }
          else {
            startTagNameToken = xmlToken;
          }
        }
      }
    }

    return false;
  }

  private void checkTagByDescriptor(final XmlTag tag) {
    String name = tag.getName();

    XmlElementDescriptor elementDescriptor;

    if (tag.getParent() instanceof XmlTag) {
      XmlTag parentTag = (XmlTag)tag.getParent();
      elementDescriptor = parentTag.getDescriptor();
      if (elementDescriptor == null) { return; }

      elementDescriptor = elementDescriptor.getElementDescriptor(tag);
      
      if (elementDescriptor instanceof AnyXmlElementDescriptor) {
        elementDescriptor = tag.getDescriptor();
      }
      if (elementDescriptor == null) {
        addElementsForTag(
          tag,
          "Element " + name + " is not allowed here",
          myResult,
          getTagProblemInfoType(tag),
          null
        );
        return;
      }
    }
    else {
      //root tag
      elementDescriptor = tag.getDescriptor();

     if (elementDescriptor == null) {
       addElementsForTag(tag, "Element " + name + " must be declared", myResult, HighlightInfoType.WRONG_REF, null);
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
        requiredAttributes.add(attribute.getDefaultName());
      }
    }

    if (requiredAttributes != null) {
      for (final String attrName : requiredAttributes) {
        if (tag.getAttribute(attrName, tag.getNamespace()) == null) {
          addElementsForTag(
            tag,
            "Element " + name + " doesn't have required attribute",
            myResult,
            HighlightInfoType.WRONG_REF,
            new IntentionAction() {
              public String getText() {
                return "Insert Required Attribute";
              }

              public String getFamilyName() {
                return "Insert Required Attribute";
              }

              public boolean isAvailable(Project project, Editor editor, PsiFile file) {
                return true;
              }

              public void invoke(final Project project, final Editor editor, PsiFile file) {
                ASTNode treeElement = SourceTreeToPsiMap.psiElementToTree(tag);
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
                template.addTextSegment(" " + attrName + "=\"");

                Expression expression = new Expression() {
                  TextResult result = new TextResult("");

                  public Result calculateResult(ExpressionContext context) {
                    return result;
                  }

                  public Result calculateQuickResult(ExpressionContext context) {
                    return null;
                  }

                  public LookupItem[] calculateLookupItems(ExpressionContext context) {
                    return new LookupItem[0];
                  }
                };
                template.addVariable("name", expression, expression, true);
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
          );
          return;
        }
      }
    }

    if (elementDescriptor instanceof Validator) {
      ((Validator)elementDescriptor).validate(tag,this);
    }
  }

  private static HighlightInfoType getTagProblemInfoType(XmlTag tag) {
    return (tag instanceof HtmlTag)?HighlightInfoType.WARNING:HighlightInfoType.WRONG_REF;
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
      addElementsForTag(tag, "Wrong root element", myResult, HighlightInfoType.WRONG_REF, null);
    }
  }

  public void visitXmlAttribute(XmlAttribute attribute) {
    XmlTag tag = attribute.getParent();

    if (attribute.isNamespaceDeclaration()) {
      checkNamespaceAttribute(attribute);
      return;
    } else if (attribute.getName().endsWith("Location")) {
      String namespace = attribute.getNamespace();

      if (namespace.equals(XmlUtil.XML_SCHEMA_INSTANCE_URI)) {
        checkSchemaLocationAttribute(attribute);
        return;
      }
    }

    XmlElementDescriptor elementDescriptor = tag.getDescriptor();
    if (elementDescriptor == null || ourDoJaxpTesting) return;
    XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor(attribute);

    String localName = attribute.getLocalName();

    if (attributeDescriptor == null) {
      myResult.add(HighlightInfo.createHighlightInfo(
        getTagProblemInfoType(tag),
          XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(SourceTreeToPsiMap.psiElementToTree(attribute)),
          "Attribute " + localName + " is not allowed here"));
    }
    else {
      checkDuplicateAttribute(tag, attribute);
      if (tag instanceof HtmlTag &&
          attribute.getValueElement() == null &&
          !HtmlUtil.isSingleHtmlAttribute(attribute.getName())
         ) {
        myResult.add(HighlightInfo.createHighlightInfo(
          HighlightInfoType.WRONG_REF,
            XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(SourceTreeToPsiMap.psiElementToTree(attribute)),
            "Empty attribute " + localName + " is not allowed")
        );
      }
    }
  }

  private void checkDuplicateAttribute(XmlTag tag, final XmlAttribute attribute) {
    XmlAttribute[] attributes = tag.getAttributes();

    for (XmlAttribute tagAttribute : attributes) {
      if (attribute != tagAttribute && Comparing.strEqual(attribute.getName(), tagAttribute.getName())) {
        String localName = attribute.getLocalName();
        HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(
          HighlightInfoType.WRONG_REF,
          XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(SourceTreeToPsiMap.psiElementToTree(attribute)),
          "Duplicate attribute " + localName);
        myResult.add(highlightInfo);

        IntentionAction intentionAction = new IntentionAction() {
          public String getText() {
            return "Remove Duplicated Attribute";
          }

          public String getFamilyName() {
            return "Remove Duplicated Attribute";
          }

          public boolean isAvailable(Project project, Editor editor, PsiFile file) {
            return true;
          }

          public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
            attribute.delete();
          }

          public boolean startInWriteAction() {
            return true;
          }
        };

        QuickFixAction.registerQuickFixAction(highlightInfo, intentionAction, null);
      }
    }
  }

  public void visitXmlTag(XmlTag tag) {
    checkReferences(tag, QuickFixProvider.NULL);
  }

  public void visitXmlAttributeValue(XmlAttributeValue value) {
    if (!(value.getParent() instanceof XmlAttribute)) return;
    XmlAttribute attribute = (XmlAttribute)value.getParent();

    XmlTag tag = attribute.getParent();

    XmlElementDescriptor elementDescriptor = tag.getDescriptor();
    if (elementDescriptor == null) return;
    XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor(attribute);
    if (attributeDescriptor == null) return;

    String error = attributeDescriptor.validateValue(value, attribute.getValue());

    if (error != null) {
      myResult.add(HighlightInfo.createHighlightInfo(
          getTagProblemInfoType(tag),
          value,
          error));
      return;
    }

    if (myRefCountHolder != null && attributeDescriptor.hasIdType()) {
      String unquotedValue = getUnquotedValue(value, tag);
      XmlTag xmlTag = myRefCountHolder.getTagById(unquotedValue);

      if (xmlTag == null ||
          !xmlTag.isValid() ||
          xmlTag == tag
         ) {
        myRefCountHolder.registerTagWithId(unquotedValue,tag);
      } else {
        XmlAttribute anotherTagIdValue = xmlTag.getAttribute("id", null);

        if (anotherTagIdValue!=null &&
            getUnquotedValue(anotherTagIdValue.getValueElement(), xmlTag).equals(unquotedValue)
           ) {
          myResult.add(HighlightInfo.createHighlightInfo(
            HighlightInfoType.WRONG_REF,
              value,
              "Duplicate id reference")
          );
          myResult.add(HighlightInfo.createHighlightInfo(
            HighlightInfoType.WRONG_REF,
              xmlTag.getAttribute("id",null).getValueElement(),
              "Duplicate id reference")
          );
          return;
        } else {
          // tag previously has that id
          myRefCountHolder.registerTagWithId(unquotedValue,tag);
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
      XmlTag xmlTag = holder.getTagById(unquotedValue);

      if (xmlTag == null || !xmlTag.isValid()) {
        return HighlightInfo.createHighlightInfo(
          HighlightInfoType.WRONG_REF,
            value,
            "Invalid id reference"
        );
      }
    }

    return null;
  }

  private static String getUnquotedValue(XmlAttributeValue value, XmlTag tag) {
    String unquotedValue = value.getText();

    if (unquotedValue.length() > 0 &&
        ( unquotedValue.charAt(0)=='"' ||
          unquotedValue.charAt(0)=='\''
        )
       ) {
      unquotedValue = unquotedValue.substring(1,unquotedValue.length()-1);
    }

    if (tag instanceof HtmlTag) {
      unquotedValue = unquotedValue.toLowerCase();
    }

    return unquotedValue;
  }

  private void checkReferences(PsiElement value, QuickFixProvider quickFixProvider) {
    PsiReference[] references = value.getReferences();

    for (final PsiReference reference : references) {
      if (reference != null) {
        if (!reference.isSoft() && reference.resolve() == null) {
          String message;
          if (reference instanceof GenericReference) {
            message = ((GenericReference)reference).getUnresolvedMessage();
          }
          else {
            message = UNKNOWN_SYMBOL;
          }

          HighlightInfo info = HighlightInfo.createHighlightInfo(
            getTagProblemInfoType(PsiTreeUtil.getParentOfType(value, XmlTag.class)),
            reference.getElement().getTextRange().getStartOffset() + reference.getRangeInElement().getStartOffset(),
            reference.getElement().getTextRange().getStartOffset() + reference.getRangeInElement().getEndOffset(),
            MessageFormat.format(message, new Object[]{reference.getCanonicalText()}));
          myResult.add(info);
          quickFixProvider.registerQuickfix(info, reference);
          if (reference instanceof QuickFixProvider) ((QuickFixProvider)reference).registerQuickfix(info, reference);
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
            "URI is not registered (Settings | IDE Settings | Resources)");
      myResult.add(info);
      QuickFixAction.registerQuickFixAction(info, new FetchExtResourceAction(), null);
      QuickFixAction.registerQuickFixAction(info, new IgnoreExtResourceAction(), null);
    }
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

    if (attribute.getLocalName().equals("noNamespaceSchemaLocation")) {
      if(ExternalResourceManagerEx.getInstanceEx().isIgnoredResource(location)) return;

      if(XmlUtil.findXmlFile(attribute.getContainingFile(),location) == null) {
        int start = attribute.getValueElement().getTextOffset();
        reportURIProblem(start,start + location.length());
      }
    } else if (attribute.getLocalName().equals("schemaLocation")) {
      StringTokenizer tokenizer = new StringTokenizer(location);
      XmlFile file = null;

      while(tokenizer.hasMoreElements()) {
        tokenizer.nextToken(); // skip namespace
        if (!tokenizer.hasMoreElements()) return;
        String url = tokenizer.nextToken();

        if(ExternalResourceManagerEx.getInstanceEx().isIgnoredResource(url)) continue;
        if (file == null) {
          file = (XmlFile)attribute.getContainingFile();
        }

        if(XmlUtil.findXmlFile(file,url) == null) {
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
      "URI is not registered (Settings | IDE Settings | Resources)");
    QuickFixAction.registerQuickFixAction(info, new FetchExtResourceAction(), null);
    QuickFixAction.registerQuickFixAction(info, new IgnoreExtResourceAction(), null);
    myResult.add(info);
  }

  public static void setDoJaxpTesting(boolean doJaxpTesting) {
    ourDoJaxpTesting = doJaxpTesting;
  }

  public void addMessage(PsiElement context, String message, int type) {
    if (message != null && message.length() > 0) {
      if (context instanceof XmlTag) {
        addElementsForTag((XmlTag)context,message,myResult, type == ERROR ? HighlightInfoType.ERROR:HighlightInfoType.WARNING,null);
      } else {
        myResult.add(HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF,context,message));
      }
    }
  }
}
