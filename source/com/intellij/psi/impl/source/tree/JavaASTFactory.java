/*
 * @author max
 */
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTFactory;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.javadoc.*;
import com.intellij.psi.impl.source.tree.java.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.java.IJavaDocElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.util.CharTable;

public class JavaASTFactory extends ASTFactory implements Constants {
  public CompositeElement createComposite(final IElementType type) {
    //TODO: Replace whole method with type.createPsiElement();
    CompositeElement element = null;
    if (type == TYPE_PARAMETER_LIST) {
      element = new TypeParameterListElement();
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

    if (element == null) {
      if(type instanceof IFileElementType) {
        element = new FileElement(type);
      } else {
        element = new CompositePsiElement(type){};
      }
    }

    return element;
  }

  public LeafElement createLeaf(final IElementType type, final CharSequence fileText, final int start, final int end, final CharTable table) {
    LeafElement element = null;
    if (type == C_STYLE_COMMENT || type == END_OF_LINE_COMMENT) {
      element = new PsiCommentImpl(type, fileText, start, end, table);
    }
    else if (type == IDENTIFIER) {
      element = new PsiIdentifierImpl(fileText, start, end, table);
    }
    else if (KEYWORD_BIT_SET.contains(type)) {
      element = new PsiKeywordImpl(type, fileText, start, end, table);
    }
    else if (type instanceof IJavaElementType) {
      element = new PsiJavaTokenImpl(type, fileText, start, end, table);
    }
    else if (type instanceof IJavaDocElementType) {
      element = new PsiDocTokenImpl(type, fileText, start, end, table);
    }

    if (element == null) {
      element = new LeafPsiElement(type, fileText, start, end, table);
    }

    return element;
  }
}