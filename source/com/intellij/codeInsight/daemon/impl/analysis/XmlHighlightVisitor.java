package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.QuickFixProvider;
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.actions.ValidateXmlActionHandler;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.xml.sax.SAXParseException;

import java.text.MessageFormat;
import java.util.*;
import java.lang.ref.WeakReference;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;

/**
 * @author Mike
 */
public class XmlHighlightVisitor extends PsiElementVisitor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor");

  private static final String UNKNOWN_SYMBOL = "Cannot resolve symbol {0}";
  private List<HighlightInfo> myResult = new ArrayList<HighlightInfo>();
  private ValidateXmlActionHandler myHandler;
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

  private long myTimeStamp;
  private VirtualFile myFile;
  private WeakReference<List<HighlightInfo>> myInfos; // last jaxp validation result

  private void runJaxpValidation(final XmlElement element, final List<HighlightInfo> result) {
    VirtualFile virtualFile = element.getContainingFile().getVirtualFile();
    if (myFile == virtualFile &&
        virtualFile != null &&
        myTimeStamp == virtualFile.getTimeStamp() &&
        myInfos!=null &&
        myInfos.get()!=null // we have validated before
        ) {
      result.addAll(myInfos.get());
      return;
    }

    PsiFile containingFile = element.getContainingFile();
    if (myHandler==null)  myHandler = new ValidateXmlActionHandler(false);
    final Project project = element.getProject();

    final Document document = PsiDocumentManager.getInstance(project).getDocument(containingFile);
    if (document==null) return;

    final List<HighlightInfo> results = new LinkedList<HighlightInfo>();
    myHandler.setErrorReporter(myHandler.new ErrorReporter() {
      public boolean filterValidationException(Exception ex) {
        super.filterValidationException(ex);
        if (ex instanceof FileNotFoundException ||
            ex instanceof MalformedURLException
            ) {
          // do not log problems caused by malformed and/or ignored external resources
          return true;
        }
        return false;
      }

      public void processError(final SAXParseException e, final boolean warning) {
        try {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              if (document.getLineCount() <= e.getLineNumber() || e.getLineNumber() <= 0) {
                return;
              }

              int offset = Math.max(0, document.getLineStartOffset(e.getLineNumber() - 1) + e.getColumnNumber() - 2);
              PsiElement currentElement = PsiDocumentManager.getInstance(project).getPsiFile(document).findElementAt(offset);
              PsiElement originalElement = currentElement;
              final String elementText = currentElement.getText();

              if (elementText.equals("</")) {
                currentElement = currentElement.getNextSibling();
              }
              else if (elementText.equals(">") || elementText.equals("=")) {
                currentElement = currentElement.getPrevSibling();
              }

              // Cannot find the declaration of element
              String localizedMessage = e.getLocalizedMessage();
              localizedMessage = localizedMessage.substring(localizedMessage.indexOf(':') + 1).trim();

              if (localizedMessage.startsWith("Cannot find the declaration of element") ||
                  localizedMessage.startsWith("Element") ||
                  localizedMessage.startsWith("Document root element") ||
                  localizedMessage.startsWith("The content of element type")
                  ) {
                currentElement = addProblemToTagName(currentElement, originalElement, localizedMessage, result, warning);
                //return;
              } else if (localizedMessage.startsWith("Value ")) {
                currentElement = addProblemToTagName(currentElement, originalElement, localizedMessage, result, warning);
                return;
              } else if (localizedMessage.startsWith("Attribute ")) {
                currentElement = PsiTreeUtil.getParentOfType(currentElement,XmlAttribute.class,false);
                final int messagePrefixLength = "Attribute ".length();

                if (currentElement==null && localizedMessage.charAt(messagePrefixLength) == '"') {
                  // extract the attribute name from message and get it from tag!
                  final int nextQuoteIndex = localizedMessage.indexOf('"', messagePrefixLength + 1);
                  String attrName = (nextQuoteIndex!=-1)?localizedMessage.substring(messagePrefixLength + 1, nextQuoteIndex):null;

                  XmlTag parent = PsiTreeUtil.getParentOfType(originalElement,XmlTag.class);
                  currentElement = parent.getAttribute(attrName,null);

                  if (currentElement!=null) {
                    currentElement = SourceTreeToPsiMap.treeElementToPsi(
                      XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(
                        (CompositeElement)SourceTreeToPsiMap.psiElementToTree(currentElement)
                      )
                    );
                  }
                }

                if (currentElement!=null) {
                  assertValidElement(currentElement, originalElement,localizedMessage);
                  result.add(HighlightInfo.createHighlightInfo(warning ? HighlightInfoType.WARNING : HighlightInfoType.ERROR,
                                                               currentElement,
                                                               localizedMessage));
                } else {
                  currentElement = addProblemToTagName(originalElement, originalElement, localizedMessage, result, warning);
                }
                return;
              } else {
                currentElement = PsiTreeUtil.getParentOfType(currentElement,XmlTag.class,false);
                assertValidElement(currentElement, originalElement,localizedMessage);
                if (currentElement!=null) {
                  result.add(HighlightInfo.createHighlightInfo(warning ? HighlightInfoType.WARNING : HighlightInfoType.ERROR,
                                                               currentElement,
                                                               localizedMessage));
                }
              }
            }
          });
        }
        catch (Exception ex) {
          if (ex instanceof ProcessCanceledException) throw (ProcessCanceledException)ex;
          LOG.error(ex);
        }
      }

    });

    myHandler.doValidate(project, element.getContainingFile());

    myFile = containingFile.getVirtualFile();
    myTimeStamp = myFile == null ? 0 : myFile.getTimeStamp();
    myInfos = new WeakReference<List<HighlightInfo>>(results);

    result.addAll(results);
  }

  private PsiElement addProblemToTagName(PsiElement currentElement,
                                     final PsiElement originalElement,
                                     final String localizedMessage,
                                     final List<HighlightInfo> result, final boolean warning) {
    currentElement = PsiTreeUtil.getParentOfType(currentElement,XmlTag.class,false);
    assertValidElement(currentElement, originalElement,localizedMessage);

    if (currentElement instanceof XmlTag) {
      addElementsForTag((XmlTag)currentElement, localizedMessage, result, warning ? HighlightInfoType.WARNING : HighlightInfoType.ERROR, null);
    }

    return currentElement;
  }

  private static void assertValidElement(PsiElement currentElement, PsiElement originalElement, String message) {
    if (currentElement==null) {
      XmlTag tag = PsiTreeUtil.getParentOfType(originalElement, XmlTag.class);
      LOG.assertTrue(
        false,
        "The validator message:"+ message+ " is bound to null node,\n" +
        "initial element:"+originalElement.getText()+",\n"+
        "parent:" + originalElement.getParent()+",\n" +
        "tag:" + (tag != null? tag.getText():"null") + ",\n" +
        "offset in tag: " + (originalElement.getTextOffset() - ((tag!=null)?tag.getTextOffset():0))
      );
    }
  }

  private void addElementsForTag(XmlTag tag,
                                 String localizedMessage,
                                 List<HighlightInfo> result,
                                 HighlightInfoType type,
                                 IntentionAction quickFixAction) {
    final CompositeElement tagElement = (CompositeElement)SourceTreeToPsiMap.psiElementToTree(tag);
    TreeElement childByRole = XmlChildRole.START_TAG_NAME_FINDER.findChild(tagElement);

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
    if (!(document.getRootTag() instanceof HtmlTag)) {
      runJaxpValidation(document, myResult);
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
      if (elementDescriptor == null) {
        addElementsForTag(tag, "Element " + name + " is not allowed here", myResult, HighlightInfoType.WRONG_REF, null);
        return;
      }
      if (elementDescriptor instanceof AnyXmlElementDescriptor) {
        elementDescriptor = tag.getDescriptor();
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
                CompositeElement treeElement = (CompositeElement)SourceTreeToPsiMap.psiElementToTree(tag);
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
          XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild((CompositeElement)SourceTreeToPsiMap.psiElementToTree(attribute)),
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
            XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild((CompositeElement)SourceTreeToPsiMap.psiElementToTree(attribute)),
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
          XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild((CompositeElement)SourceTreeToPsiMap.psiElementToTree(attribute)),
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
}
