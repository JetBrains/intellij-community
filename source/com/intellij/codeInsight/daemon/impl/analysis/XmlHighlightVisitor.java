package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.Validator;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.IgnoreExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import com.intellij.lang.ASTNode;
import com.intellij.util.SmartList;

import java.text.MessageFormat;
import java.util.*;

/**
 * @author Mike
 */
public class XmlHighlightVisitor extends PsiElementVisitor implements Validator.ValidationHost {
  private static final String UNKNOWN_SYMBOL = "Cannot resolve symbol {0}";
  private List<HighlightInfo> myResult = new SmartList<HighlightInfo>();

  private static boolean ourDoJaxpTesting;
  private static final Key<HashMap<String,XmlTag>> ID_TO_TAG_MAP_KEY = Key.create("ID_TO_TAG_MAP");

  public XmlHighlightVisitor() {
  }

  public List<HighlightInfo> getResult() {
    return myResult;
  }

  public void clearResult() {
    myResult.clear();
  }

  public void visitReferenceExpression(PsiReferenceExpression expression) {
  }

  private static void addElementsForTag(XmlTag tag,
                                 String localizedMessage,
                                 List<HighlightInfo> result,
                                 HighlightInfoType type,
                                 IntentionAction quickFixAction) {
    final ASTNode tagElement = SourceTreeToPsiMap.psiElementToTree(tag);
    ASTNode childByRole = XmlChildRole.START_TAG_NAME_FINDER.findChild(tagElement);

    if(childByRole != null) {
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(type, childByRole, localizedMessage);
      result.add(highlightInfo);
      QuickFixAction.registerQuickFixAction(highlightInfo, quickFixAction);
    }

    childByRole = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(tagElement);

    if(childByRole != null) {
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(type, childByRole, localizedMessage);
      QuickFixAction.registerQuickFixAction(highlightInfo, quickFixAction);
      result.add(highlightInfo);
    }
  }

  public void visitXmlToken(XmlToken token) {
    if (token.getTokenType() == XmlTokenType.XML_NAME) {
      final PsiElement element = token.getPrevSibling();

      if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_START_TAG_START) {
        final PsiElement parent = element.getParent();

        if (parent instanceof XmlTag) {
          XmlTag tag = (XmlTag)parent;
          checkTag(tag);
        }
      }
    }
  }

  public void visitXmlDocument(XmlDocument document) {
    final XmlTag rootTag = document.getRootTag();
    final XmlNSDescriptor nsDescriptor = rootTag == null ? null : rootTag.getNSDescriptor(rootTag.getNamespace(), false);

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

  private boolean checkTagIsClosed(XmlTag tag) {
    final PsiElement[] children = tag.getChildren();
    String name = tag.getName();

    boolean insideEndTag = false;

    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];

      if (child instanceof XmlToken) {
        XmlToken xmlToken = (XmlToken)child;
        if (xmlToken.getTokenType() == XmlTokenType.XML_EMPTY_ELEMENT_END) return true;
        if (xmlToken.getTokenType() == XmlTokenType.XML_END_TAG_START) {
          insideEndTag = true;
        }

        if (insideEndTag && xmlToken.getTokenType() == XmlTokenType.XML_NAME) {
          String text = xmlToken.getText();
          if (tag instanceof HtmlTag) {
            text = text.toLowerCase();
            name = name.toLowerCase();
          }

          if (text.equals(name)) return true;

          myResult.add(HighlightInfo.createHighlightInfo(
            HighlightInfoType.ERROR,
            xmlToken,
            "Wrong closing tag name"));

          return false;
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
        addElementsForTag(tag, "Element " + name + " is not allowed here", myResult, HighlightInfoType.WRONG_REF, null);
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

    final XmlAttributeDescriptor[] attributeDescriptors = elementDescriptor.getAttributesDescriptors();
    Set<String> requiredAttributes = null;

    for (int i = 0; i < attributeDescriptors.length; i++) {
      XmlAttributeDescriptor attribute = attributeDescriptors[i];
      if (attribute != null && attribute.isRequired()) {
        if (requiredAttributes == null) {
          requiredAttributes = new HashSet<String>();
        }
        requiredAttributes.add(attribute.getDefaultName());
      }
    }

    if (requiredAttributes != null) {
      for (Iterator<String> iterator = requiredAttributes.iterator(); iterator.hasNext();) {
        final String attrName = iterator.next();

        if (tag.getAttribute(attrName, XmlUtil.ALL_NAMESPACE) == null) {
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

              public void invoke(final Project project, final Editor editor, final PsiFile file) {
                ASTNode treeElement = SourceTreeToPsiMap.psiElementToTree(tag);
                PsiElement anchor = SourceTreeToPsiMap.treeElementToPsi(
                  XmlChildRole.EMPTY_TAG_END_FINDER.findChild(treeElement)
                );

                if (anchor==null) {
                  anchor = SourceTreeToPsiMap.treeElementToPsi(
                    XmlChildRole.START_TAG_END_FINDER.findChild(treeElement)
                  );
                }

                if (anchor == null) return;

                final Template template = TemplateManager.getInstance(project).createTemplate("","");
                template.addTextSegment(" "+ attrName + "=\"");

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
                template.addVariable("name",expression,expression,true);
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
    final XmlTag tag = attribute.getParent();

    if (attribute.isNamespaceDeclaration()) {
      checkNamespaceAttribute(attribute);
      return;
    }

    if (attribute.getName().endsWith("Location")) {
      final String namespace = attribute.getNamespace();
      // TODO[ik]: check schema attributes
      if (namespace.equals(XmlUtil.XML_SCHEMA_INSTANCE_URI)) return;
    }

    final XmlElementDescriptor elementDescriptor = tag.getDescriptor();
    if (elementDescriptor == null || ourDoJaxpTesting) return;
    final XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor(attribute);

    final String localName = attribute.getLocalName();

    if (attributeDescriptor == null) {
      myResult.add(HighlightInfo.createHighlightInfo(
        HighlightInfoType.WRONG_REF,
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

  private void checkDuplicateAttribute(XmlTag tag, XmlAttribute attribute) {
    XmlAttribute[] attributes = tag.getAttributes();

    for(int i = 0; i < attributes.length; i++){
      XmlAttribute tagAttribute = attributes[i];

      if (attribute != tagAttribute && Comparing.strEqual(attribute.getName(),tagAttribute.getName())) {
        final String localName = attribute.getLocalName();
        myResult.add(HighlightInfo.createHighlightInfo(
          HighlightInfoType.WRONG_REF,
          XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(SourceTreeToPsiMap.psiElementToTree(attribute)),
          "Duplicate attribute " + localName));
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

    final XmlElementDescriptor elementDescriptor = tag.getDescriptor();
    if (elementDescriptor == null) return;
    final XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor(attribute);
    if (attributeDescriptor == null) return;

    String error = attributeDescriptor.validateValue(value, attribute.getValue());

    if (error != null) {
      myResult.add(HighlightInfo.createHighlightInfo(
        HighlightInfoType.WRONG_REF,
          value,
          error));
      return;
    }

    if (attributeDescriptor.hasIdType()) {
      HashMap<String,XmlTag> idToTagMap = getIdToTagMap(tag);

      String unquotedValue = getUnquotedValue(value, tag);
      final XmlTag xmlTag = idToTagMap.get(unquotedValue);

      if (xmlTag == null ||
          !xmlTag.isValid() ||
          xmlTag == tag
         ) {
        idToTagMap.put(unquotedValue,tag);
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
          idToTagMap.put(unquotedValue,tag);
        }
      }
    }

    if (attributeDescriptor.hasIdRefType()) {
      HashMap<String,XmlTag> idToTagMap = getIdToTagMap(tag);

      String unquotedValue = getUnquotedValue(value, tag);
      final XmlTag xmlTag = idToTagMap.get(unquotedValue);

      if (xmlTag == null || !xmlTag.isValid()) {
        myResult.add(HighlightInfo.createHighlightInfo(
          HighlightInfoType.WRONG_REF,
            value,
            "Invalid id reference")
        );
      }
    }

    QuickFixProvider quickFixProvider = QuickFixProvider.NULL;
    if (attributeDescriptor instanceof QuickFixProvider) quickFixProvider = (QuickFixProvider)attributeDescriptor;

    checkReferences(value, quickFixProvider);

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

  private static HashMap<String, XmlTag> getIdToTagMap(XmlTag tag) {
    XmlDocument document = PsiTreeUtil.getParentOfType(tag, XmlDocument.class);

    HashMap<String,XmlTag> idToTagMap = document.getUserData(ID_TO_TAG_MAP_KEY);
    if (idToTagMap==null) {
      idToTagMap = new HashMap<String, XmlTag>();
      document.putUserData(ID_TO_TAG_MAP_KEY,idToTagMap);
    }
    return idToTagMap;
  }

  private void checkReferences(PsiElement value, QuickFixProvider quickFixProvider) {
    PsiReference[] references = value.getReferences();

    for(int i = 0; i < references.length; i++){
      final PsiReference reference = references[i];
      if (reference != null) {
        if (!reference.isSoft() && reference.resolve() == null) {
          final String message;
          if(reference instanceof GenericReference)
            message = ((GenericReference)reference).getUnresolvedMessage();
          else
            message = UNKNOWN_SYMBOL;
          HighlightInfo info = HighlightInfo.createHighlightInfo(
                      HighlightInfoType.WRONG_REF,
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
    final String uri = xmlDoctype.getDtdUri();
    if (uri == null || ExternalResourceManagerEx.getInstanceEx().isIgnoredResource(uri)) return;

    final XmlFile xmlFile = XmlUtil.findXmlFile(xmlDoctype.getContainingFile(), uri);
    if (xmlFile == null) {
      final HighlightInfo info = HighlightInfo.createHighlightInfo(
            HighlightInfoType.WRONG_REF,
            xmlDoctype.getDtdUrlElement().getTextRange().getStartOffset() + 1,
            xmlDoctype.getDtdUrlElement().getTextRange().getEndOffset() - 1,
            "URI is not registered (Settings | IDE Settings | Resources)");
      myResult.add(info);
      QuickFixAction.registerQuickFixAction(info, new FetchExtResourceAction());
      QuickFixAction.registerQuickFixAction(info, new IgnoreExtResourceAction());
    }
  }

  private void checkNamespaceAttribute(XmlAttribute attribute) {
    String namespace = null;

    if (attribute.isNamespaceDeclaration()) {
      namespace = attribute.getValue();
    }

    if(namespace == null || namespace.length() < 1|| ExternalResourceManagerEx.getInstanceEx().isIgnoredResource(namespace)) return;
    final XmlTag declarationTag = attribute.getParent();

    if(declarationTag.getNSDescriptor(namespace, true) != null) return;

    String attributeValue = declarationTag.getAttributeValue("targetNamespace");
    if (attributeValue != null && attributeValue.equals(namespace)) {
      // we referencing ns while defining it
      return;
    }

    // check if the namespace is defined
    final XmlAttributeValue element = attribute.getValueElement();
    if(element == null) return;
    final int start = element.getTextRange().getStartOffset() + 1;
    int end = element.getTextRange().getEndOffset() - 1;
    if (start > end) {
      end = start;
    }
    final HighlightInfo info = HighlightInfo.createHighlightInfo(
      HighlightInfoType.WRONG_REF,
        start,
        end,
      "URI is not registered (Settings | IDE Settings | Resources)");
    QuickFixAction.registerQuickFixAction(info, new FetchExtResourceAction());
    QuickFixAction.registerQuickFixAction(info, new IgnoreExtResourceAction());
    myResult.add(info);
  }

  public static void setDoJaxpTesting(final boolean doJaxpTesting) {
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
