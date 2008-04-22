package com.intellij.psi.impl.source;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.TokenSet;

public interface Constants extends ElementType {

  PsiElementArrayConstructor<PsiClass> PSI_CLASS_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiClass>() {
    public PsiClass[] newPsiElementArray(int length) {
      return length != 0 ? new PsiClass[length] : PsiClass.EMPTY_ARRAY;
    }
  };

  PsiElementArrayConstructor<PsiField> PSI_FIELD_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiField>() {
    public PsiField[] newPsiElementArray(int length) {
      return length != 0 ? new PsiField[length] : PsiField.EMPTY_ARRAY;
    }
  };

  PsiElementArrayConstructor<PsiMethod> PSI_METHOD_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiMethod>() {
    public PsiMethod[] newPsiElementArray(int length) {
      return length != 0 ? new PsiMethod[length] : PsiMethod.EMPTY_ARRAY;
    }
  };

  PsiElementArrayConstructor<PsiClassInitializer> PSI_CLASS_INITIALIZER_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiClassInitializer>() {
    public PsiClassInitializer[] newPsiElementArray(int length) {
      return length != 0 ? new PsiClassInitializer[length] : PsiClassInitializer.EMPTY_ARRAY;
    }
  };

  PsiElementArrayConstructor<PsiParameter> PSI_PARAMETER_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiParameter>() {
    public PsiParameter[] newPsiElementArray(int length) {
      return length != 0 ? new PsiParameter[length] : PsiParameter.EMPTY_ARRAY;
    }
  };

  PsiElementArrayConstructor<PsiCatchSection> PSI_CATCH_SECTION_ARRAYS_CONSTRUCTOR = new PsiElementArrayConstructor<PsiCatchSection>() {
    public PsiCatchSection[] newPsiElementArray(int length) {
      return length != 0 ? new PsiCatchSection[length] : PsiCatchSection.EMPTY_ARRAY;
    }
  };

  PsiElementArrayConstructor<PsiJavaCodeReferenceElement> PSI_REFERENCE_ELEMENT_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiJavaCodeReferenceElement>() {
    public PsiJavaCodeReferenceElement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiJavaCodeReferenceElement[length] : PsiJavaCodeReferenceElement.EMPTY_ARRAY;
    }
  };

  PsiElementArrayConstructor<PsiStatement> PSI_STATEMENT_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiStatement>() {
    public PsiStatement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiStatement[length] : PsiStatement.EMPTY_ARRAY;
    }
  };

  PsiElementArrayConstructor<PsiExpression> PSI_EXPRESSION_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiExpression>() {
    public PsiExpression[] newPsiElementArray(int length) {
      return length != 0 ? new PsiExpression[length] : PsiExpression.EMPTY_ARRAY;
    }
  };

  PsiElementArrayConstructor<PsiImportStatement> PSI_IMPORT_STATEMENT_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiImportStatement>() {
    public PsiImportStatement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiImportStatement[length] : PsiImportStatement.EMPTY_ARRAY;
    }
  };

  PsiElementArrayConstructor<PsiImportStaticStatement> PSI_IMPORT_STATIC_STATEMENT_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiImportStaticStatement>() {
    public PsiImportStaticStatement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiImportStaticStatement[length] : PsiImportStaticStatement.EMPTY_ARRAY;
    }
  };


  PsiElementArrayConstructor<PsiImportStatementBase> PSI_IMPORT_STATEMENT_BASE_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiImportStatementBase>() {
    public PsiImportStatementBase[] newPsiElementArray(int length) {
      return length != 0 ? new PsiImportStatementBase[length] : PsiImportStatementBase.EMPTY_ARRAY;
    }
  };

  PsiElementArrayConstructor<PsiAnnotationMemberValue> PSI_ANNOTATION_MEMBER_VALUE_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiAnnotationMemberValue>() {
    public PsiAnnotationMemberValue[] newPsiElementArray(int length) {
      return length != 0 ? new PsiAnnotationMemberValue[length] : PsiAnnotationMemberValue.EMPTY_ARRAY;
    }
  };

  PsiElementArrayConstructor<PsiNameValuePair> PSI_NAME_VALUE_PAIR_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiNameValuePair>() {
    public PsiNameValuePair[] newPsiElementArray(int length) {
      return length != 0 ? new PsiNameValuePair[length] : PsiNameValuePair.EMPTY_ARRAY;
    }
  };

  PsiElementArrayConstructor<PsiAnnotation> PSI_ANNOTATION_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiAnnotation>() {
    public PsiAnnotation[] newPsiElementArray(int length) {
      return length != 0 ? new PsiAnnotation[length] : PsiAnnotation.EMPTY_ARRAY;
    }
  };

  TokenSet CLASS_BIT_SET = TokenSet.create(CLASS, ANONYMOUS_CLASS, ENUM_CONSTANT_INITIALIZER);
  TokenSet FIELD_BIT_SET = TokenSet.create(FIELD, ENUM_CONSTANT);
  TokenSet METHOD_BIT_SET = TokenSet.create(METHOD, ANNOTATION_METHOD);
  TokenSet CLASS_INITIALIZER_BIT_SET = TokenSet.create(CLASS_INITIALIZER);
  TokenSet PARAMETER_BIT_SET = TokenSet.create(PARAMETER);
  TokenSet CATCH_SECTION_BIT_SET = TokenSet.create(CATCH_SECTION);
  TokenSet JAVA_CODE_REFERENCE_BIT_SET = TokenSet.create(JAVA_CODE_REFERENCE);
  TokenSet NAME_VALUE_PAIR_BIT_SET = TokenSet.create(NAME_VALUE_PAIR);
  TokenSet ANNOTATION_BIT_SET = TokenSet.create(ANNOTATION);
}
