package com.intellij.psi.impl.source.tree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.html.HtmlDocumentImpl;
import com.intellij.psi.impl.source.html.HtmlTagImpl;
import com.intellij.psi.impl.source.javadoc.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspTemplateDeclaration;
import com.intellij.psi.impl.source.jsp.jspJava.JspTemplateStatement;
import com.intellij.psi.impl.source.jsp.jspXml.JspCommentImpl;
import com.intellij.psi.impl.source.jsp.jspXml.JspXmlRootTag;
import com.intellij.psi.impl.source.jsp.jspXml.JspXmlDocument;
import com.intellij.psi.impl.source.tree.java.*;
import com.intellij.psi.impl.source.xml.*;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.jsp.JspTokenType;
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.java.IJavaDocElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.psi.tree.jsp.IJspElementType;
import com.intellij.psi.tree.xml.IXmlLeafElementType;
import com.intellij.util.CharTable;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class Factory implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.Factory");

  private static final List<TreeElementFactory> ourElementFactories = new ArrayList<TreeElementFactory>();

  public static void addElementFactory(TreeElementFactory factory) {
    ourElementFactories.add(factory);
  }

  public static LeafElement createSingleLeafElement(IElementType type, char[] buffer, int startOffset, int endOffset, CharTable table, PsiManager manager, PsiFile originalFile) {
    final LeafElement newElement;
    final DummyHolder dummyHolder = new DummyHolder(manager, table, type.getLanguage());
    dummyHolder.setOriginalFile(originalFile);
    dummyHolder.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, PsiUtil.getLanguageLevel(originalFile));
    final FileElement holderElement = dummyHolder.getTreeElement();
    newElement = Factory.createLeafElement(type, buffer, startOffset, endOffset, -1, holderElement.getCharTable());
    TreeUtil.addChildren(holderElement, newElement);
    CodeEditUtil.setNodeGenerated(newElement, true);
    return newElement;
  }

  public static LeafElement createSingleLeafElement(IElementType type, char[] buffer, int startOffset, int endOffset, CharTable table, PsiManager manager, boolean generatedFlag) {
    final LeafElement newElement;
    final FileElement holderElement = new DummyHolder(manager, table, type.getLanguage()).getTreeElement();
    newElement = Factory.createLeafElement(type, buffer, startOffset, endOffset, -1, holderElement.getCharTable());
    TreeUtil.addChildren(holderElement, newElement);
    if(generatedFlag) CodeEditUtil.setNodeGenerated(newElement, true);
    return newElement;
  }

  public static LeafElement createSingleLeafElement(IElementType type, char[] buffer, int startOffset, int endOffset, CharTable table, PsiManager manager) {
    return createSingleLeafElement(type, buffer, startOffset, endOffset, table, manager, true);
  }


  public static LeafElement createLeafElement(IElementType type, char[] buffer, int startOffset, int endOffset, int lexerState, CharTable table) {
    LeafElement element = null;
    if (type instanceof IChameleonElementType) {
      element = new ChameleonElement(type, buffer, startOffset, endOffset, lexerState);
    }
    else if (type instanceof IXmlLeafElementType) {
      element = new XmlTokenImpl(type, buffer, startOffset, endOffset, lexerState, table);
    }
    else if (type == PLAIN_TEXT) {
      element = new PsiPlainTextImpl(buffer, startOffset, endOffset, lexerState);
    }
    else if (type == WHITE_SPACE) {
      element = new PsiWhiteSpaceImpl(buffer, startOffset, endOffset, lexerState, table);
    }
    else if (type == C_STYLE_COMMENT || type == END_OF_LINE_COMMENT) {
      element = new PsiCommentImpl(type, buffer, startOffset, endOffset, lexerState, table);
    }
    else if (type == IDENTIFIER) {
      element = new PsiIdentifierImpl(buffer, startOffset, endOffset, lexerState, table);
    }
    else if (type == JspTokenType.JSP_COMMENT) {
      element = new JspCommentImpl(buffer, startOffset, endOffset, lexerState, table);
    }
    else {
      if (KEYWORD_BIT_SET.contains(type)) {
        element = new PsiKeywordImpl(type, buffer, startOffset, endOffset, lexerState, table);
      }
      else if (type instanceof IJavaElementType) {
        element = new PsiJavaTokenImpl(type, buffer, startOffset, endOffset, lexerState, table);
      }
      else if (type instanceof IJspElementType) {
        element = new XmlTokenImpl(type, buffer, startOffset, endOffset, lexerState, table);
      }
      else if (type instanceof IJavaDocElementType) {
        element = new PsiDocTokenImpl(type, buffer, startOffset, endOffset, lexerState, table);
      }
      else {
        for (int size = ourElementFactories.size(), i = 0; i < size; i++) {
          TreeElementFactory elementFactory = ourElementFactories.get(i);
          if (elementFactory.isMyElementType(type)) {
            element = elementFactory.createLeafElement(type, buffer, startOffset, endOffset, lexerState, table);
          }
        }
        //LOG.error("Unknown leaf:" + type);
        //return null;
        if (element == null) {
          element = new LeafPsiElement(type, buffer, startOffset, endOffset, lexerState, table);
        }
      }
    }
    LOG.assertTrue(element.getElementType() == type);
    return element;
  }

  @NotNull
  public static CompositeElement createCompositeElement(IElementType type) {

    //TODO: Replace whole method with type.createPsiElement();
    CompositeElement element = null;
    if (type == TYPE_PARAMETER_LIST) {
      element = new TypeParameterListElement();
    }
    else if (type == XML_TAG) {
      element = new XmlTagImpl();
    } else if (type == XML_CONDITIONAL_SECTION) {
      element = new XmlConditionalSectionImpl();
    }
    else if (type == HTML_TAG) {
      element = new HtmlTagImpl();
    }
    else if (type == XML_TEXT) {
      element = new XmlTextImpl();
    }
    else if (type == XML_PROCESSING_INSTRUCTION) {
      element = new XmlProcessingInstructionImpl();
    }
    else if (type == TYPE_PARAMETER) {
      element = new TypeParameterElement();
    }
    else if (type == EXTENDS_BOUND_LIST) {
      element = new TypeParameterExtendsBoundsListElement();
    }
    else if (type == ERROR_ELEMENT) {
      element = new PsiErrorElementImpl();
    }
    else if (type == JAVA_FILE) {
      element = new JavaFileElement();
    }
    else if (type == PLAIN_TEXT_FILE) {
      element = new PlainTextFileElement();
    }
    else if (type == XML_FILE) {
      element = new XmlFileElement(type);
    }
    else if (type == JSP_TEMPLATE) {
      element = new XmlFileElement(type);
    }
    else if (type == DTD_FILE) {
      element = new XmlFileElement(type);
    }
    else if (type == XHTML_FILE) {
      element = new XmlFileElement(type);
    }
    else if (type == JSPX_FILE) {
      element = new XmlFileElement(type);
    }
    else if (type == JspElementType.JSP_ROOT_TAG) {
      element = new JspXmlRootTag();
    }
    else if (type == JspElementType.JSP_DOCUMENT) {
      element = new JspXmlDocument();
    }
    else if (type == HTML_FILE) {
      element = new HtmlFileElement();
    }
    else if (type == CODE_FRAGMENT) {
      element = new CodeFragmentElement();
    }
    else if (type == DUMMY_HOLDER) {
      element = new DummyHolderElement();
    }
    else if (type == JavaDocElementType.DOC_COMMENT) {
      element = new PsiDocCommentImpl();
    }
    else if (type == DOC_TAG) {
      element = new PsiDocTagImpl();
    }
    else if (type == DOC_TAG_VALUE_TOKEN) {
      element = new PsiDocTagValueImpl();
    }
    else if (type == DOC_METHOD_OR_FIELD_REF) {
      element = new PsiDocMethodOrFieldRef();
    }
    else if (type == DOC_PARAMETER_REF) {
      element = new PsiDocParamRef();
    }
    else if (type == DOC_INLINE_TAG) {
      element = new PsiInlineDocTagImpl();
    }
    else if (type == CLASS) {
      element = new ClassElement(type);
    }
    else if (type == ANONYMOUS_CLASS) {
      element = new AnonymousClassElement();
    }
    else if (type == ENUM_CONSTANT_INITIALIZER) {
      element = new EnumConstantInitializerElement();
    }
    else if (type == FIELD) {
      element = new FieldElement();
    }
    else if (type == ENUM_CONSTANT) {
      element = new EnumConstantElement();
    }
    else if (type == METHOD) {
      element = new MethodElement();
    }
    else if (type == LOCAL_VARIABLE) {
      element = new PsiLocalVariableImpl();
    }
    else if (type == PARAMETER) {
      element = new ParameterElement();
    }
    else if (type == PARAMETER_LIST) {
      element = new ParameterListElement();
    }
    else if (type == CLASS_INITIALIZER) {
      element = new ClassInitializerElement();
    }
    else if (type == PACKAGE_STATEMENT) {
      element = new PsiPackageStatementImpl();
    }
    else if (type == IMPORT_LIST) {
      element = new ImportListElement();
    }
    else if (type == IMPORT_STATEMENT) {
      element = new ImportStatementElement();
    }
    else if (type == IMPORT_STATIC_STATEMENT) {
      element = new ImportStaticStatementElement();
    }
    else if (type == IMPORT_STATIC_REFERENCE) {
      element = new PsiImportStaticReferenceElementImpl();
    }
    else if (type == JAVA_CODE_REFERENCE) {
      element = new PsiJavaCodeReferenceElementImpl();
    }
    else if (type == REFERENCE_PARAMETER_LIST) {
      element = new PsiReferenceParameterListImpl();
    }
    else if (type == TYPE) {
      element = new PsiTypeElementImpl();
    }
    else if (type == MODIFIER_LIST) {
      element = new ModifierListElement();
    }
    else if (type == EXTENDS_LIST) {
      element = new ExtendsListElement();
    }
    else if (type == IMPLEMENTS_LIST) {
      element = new ImplementsListElement();
    }
    else if (type == THROWS_LIST) {
      element = new PsiThrowsListImpl();
    }
    else if (type == EXPRESSION_LIST) {
      element = new PsiExpressionListImpl();
    }
    else if (type == REFERENCE_EXPRESSION) {
      element = new PsiReferenceExpressionImpl();
    }
    else if (type == LITERAL_EXPRESSION) {
      element = new PsiLiteralExpressionImpl();
    }
    else if (type == THIS_EXPRESSION) {
      element = new PsiThisExpressionImpl();
    }
    else if (type == SUPER_EXPRESSION) {
      element = new PsiSuperExpressionImpl();
    }
    else if (type == PARENTH_EXPRESSION) {
      element = new PsiParenthesizedExpressionImpl();
    }
    else if (type == METHOD_CALL_EXPRESSION) {
      element = new PsiMethodCallExpressionImpl();
    }
    else if (type == TYPE_CAST_EXPRESSION) {
      element = new PsiTypeCastExpressionImpl();
    }
    else if (type == PREFIX_EXPRESSION) {
      element = new PsiPrefixExpressionImpl();
    }
    else if (type == POSTFIX_EXPRESSION) {
      element = new PsiPostfixExpressionImpl();
    }
    else if (type == BINARY_EXPRESSION) {
      element = new PsiBinaryExpressionImpl();
    }
    else if (type == CONDITIONAL_EXPRESSION) {
      element = new PsiConditionalExpressionImpl();
    }
    else if (type == ASSIGNMENT_EXPRESSION) {
      element = new PsiAssignmentExpressionImpl();
    }
    else if (type == NEW_EXPRESSION) {
      element = new PsiNewExpressionImpl();
    }
    else if (type == ARRAY_ACCESS_EXPRESSION) {
      element = new PsiArrayAccessExpressionImpl();
    }
    else if (type == ARRAY_INITIALIZER_EXPRESSION) {
      element = new PsiArrayInitializerExpressionImpl();
    }
    else if (type == INSTANCE_OF_EXPRESSION) {
      element = new PsiInstanceOfExpressionImpl();
    }
    else if (type == CLASS_OBJECT_ACCESS_EXPRESSION) {
      element = new PsiClassObjectAccessExpressionImpl();
    }
    else if (type == EMPTY_EXPRESSION) {
      element = new PsiEmptyExpressionImpl();
    }
    else if (type == EMPTY_STATEMENT) {
      element = new PsiEmptyStatementImpl();
    }
    else if (type == BLOCK_STATEMENT) {
      element = new PsiBlockStatementImpl();
    }
    else if (type == EXPRESSION_STATEMENT) {
      element = new PsiExpressionStatementImpl();
    }
    else if (type == EXPRESSION_LIST_STATEMENT) {
      element = new PsiExpressionListStatementImpl();
    }
    else if (type == DECLARATION_STATEMENT) {
      element = new PsiDeclarationStatementImpl();
    }
    else if (type == IF_STATEMENT) {
      element = new PsiIfStatementImpl();
    }
    else if (type == WHILE_STATEMENT) {
      element = new PsiWhileStatementImpl();
    }
    else if (type == FOR_STATEMENT) {
      element = new PsiForStatementImpl();
    }
    else if (type == FOREACH_STATEMENT) {
      element = new PsiForeachStatementImpl();
    }
    else if (type == DO_WHILE_STATEMENT) {
      element = new PsiDoWhileStatementImpl();
    }
    else if (type == SWITCH_STATEMENT) {
      element = new PsiSwitchStatementImpl();
    }
    else if (type == SWITCH_LABEL_STATEMENT) {
      element = new PsiSwitchLabelStatementImpl();
    }
    else if (type == BREAK_STATEMENT) {
      element = new PsiBreakStatementImpl();
    }
    else if (type == CONTINUE_STATEMENT) {
      element = new PsiContinueStatementImpl();
    }
    else if (type == RETURN_STATEMENT) {
      element = new PsiReturnStatementImpl();
    }
    else if (type == THROW_STATEMENT) {
      element = new PsiThrowStatementImpl();
    }
    else if (type == SYNCHRONIZED_STATEMENT) {
      element = new PsiSynchronizedStatementImpl();
    }
    else if (type == ASSERT_STATEMENT) {
      element = new PsiAssertStatementImpl();
    }
    else if (type == TRY_STATEMENT) {
      element = new PsiTryStatementImpl();
    }
    else if (type == LABELED_STATEMENT) {
      element = new PsiLabeledStatementImpl();
    }
    else if (type == CODE_BLOCK) {
      element = new PsiCodeBlockImpl();
    }
    else if (type == CATCH_SECTION) {
      element = new PsiCatchSectionImpl();
    }
    else if (type == XML_DOCUMENT) {
      element = new XmlDocumentImpl();
    } else if (type == HTML_DOCUMENT) {
      element = new HtmlDocumentImpl();
    }
    else if (type == XML_PROLOG) {
      element = new XmlPrologImpl();
    }
    else if (type == XML_DECL) {
      element = new XmlDeclImpl();
    }
    else if (type == XML_ATTRIBUTE) {
      element = new XmlAttributeImpl();
    }
    else if (type == XML_ATTRIBUTE_VALUE) {
      element = new XmlAttributeValueImpl();
    }
    else if (type == XML_COMMENT) {
      element = new XmlCommentImpl();
    }
    else if (type == XML_DOCTYPE) {
      element = new XmlDoctypeImpl();
    }
    else if (type == XML_MARKUP_DECL) {
      element = new XmlMarkupDeclImpl();
    }
    else if (type == XML_ELEMENT_DECL) {
      element = new XmlElementDeclImpl();
    }
    else if (type == XML_ENTITY_DECL) {
      element = new XmlEntityDeclImpl();
    }
    else if (type == XML_ATTLIST_DECL) {
      element = new XmlAttlistDeclImpl();
    }
    else if (type == XML_ATTRIBUTE_DECL) {
      element = new XmlAttributeDeclImpl();
    }
    else if (type == XML_NOTATION_DECL) {
      element = new XmlNotationDeclImpl();
    }
    else if (type == XML_ELEMENT_CONTENT_SPEC) {
      element = new XmlElementContentSpecImpl();
    }
    else if (type == XML_ENTITY_REF) {
      element = new XmlEntityRefImpl();
    }
    else if (type == XML_ENUMERATED_TYPE) {
      element = new XmlEnumeratedTypeImpl();
    }
    else if (type == XML_PROCESSING_INSTRUCTION) {
      element = new XmlProcessingInstructionImpl();
    }
    else if (type == ANNOTATION_METHOD) {
      element = new AnnotationMethodElement();
    }
    else if (type == ANNOTATION) {
      element = new AnnotationElement();
    }
    else if (type == ANNOTATION_ARRAY_INITIALIZER) {
      element = new PsiArrayInitializerMemberValueImpl();
    }
    else if (type == NAME_VALUE_PAIR) {
      element = new PsiNameValuePairImpl();
    }
    else if (type == ANNOTATION_PARAMETER_LIST) {
      element = new PsiAnnotationParameterListImpl();
    }
    else if(type instanceof IFileElementType) {
      element = new FileElement(type);
    }
    else{
      for (int size = ourElementFactories.size(), i = 0; i < size; i++) {
        TreeElementFactory elementFactory = ourElementFactories.get(i);
        if (elementFactory.isMyElementType(type)) {
          element = elementFactory.createCompositeElement(type);
        }
      }

      if (element == null) {
        //LOG.assertTrue(false, "Unknown composite element type:" + BitSetUtil.toString(ElementType.class, type));
        element = new CompositePsiElement(type){};
      }
    }
    if(element.getElementType() != type)
      LOG.error("Type created: " + element.getElementType() + " wanted: " + type);
    return element;
  }

  public static CompositeElement createErrorElement(String description) {
    CompositeElement errorElement = createCompositeElement(ERROR_ELEMENT);
    ((PsiErrorElementImpl)errorElement).setErrorDescription(description);
    return errorElement;
  }

  public static CompositeElement createCompositeElement(final IElementType type,
                                                        final CharTable charTableByTree,
                                                        final PsiManager manager) {
    final FileElement treeElement = new DummyHolder(manager, null, charTableByTree).getTreeElement();
    final CompositeElement composite = createCompositeElement(type);
    TreeUtil.addChildren(treeElement, composite);
    return composite;
  }

  public static void createSingleLeafElement() {
  }
}
