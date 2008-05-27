package com.intellij.psi.impl.source;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.TokenSet;

public interface Constants extends ElementType {

  PsiElementArrayConstructor<PsiClass> PSI_CLASS_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiClass>() {
    public PsiClass[] newPsiElementArray(int length) {
      return length == 0 ? PsiClass.EMPTY_ARRAY : new PsiClass[length];
    }
  };

  PsiElementArrayConstructor<PsiField> PSI_FIELD_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiField>() {
    public PsiField[] newPsiElementArray(int length) {
      return length == 0 ? PsiField.EMPTY_ARRAY : new PsiField[length];
    }
  };

  PsiElementArrayConstructor<PsiMethod> PSI_METHOD_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiMethod>() {
    public PsiMethod[] newPsiElementArray(int length) {
      return length == 0 ? PsiMethod.EMPTY_ARRAY : new PsiMethod[length];
    }
  };

  PsiElementArrayConstructor<PsiClassInitializer> PSI_CLASS_INITIALIZER_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiClassInitializer>() {
    public PsiClassInitializer[] newPsiElementArray(int length) {
      return length == 0 ? PsiClassInitializer.EMPTY_ARRAY : new PsiClassInitializer[length];
    }
  };

  PsiElementArrayConstructor<PsiParameter> PSI_PARAMETER_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiParameter>() {
    public PsiParameter[] newPsiElementArray(int length) {
      return length == 0 ? PsiParameter.EMPTY_ARRAY : new PsiParameter[length];
    }
  };

  PsiElementArrayConstructor<PsiCatchSection> PSI_CATCH_SECTION_ARRAYS_CONSTRUCTOR = new PsiElementArrayConstructor<PsiCatchSection>() {
    public PsiCatchSection[] newPsiElementArray(int length) {
      return length == 0 ? PsiCatchSection.EMPTY_ARRAY : new PsiCatchSection[length];
    }
  };

  PsiElementArrayConstructor<PsiJavaCodeReferenceElement> PSI_REFERENCE_ELEMENT_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiJavaCodeReferenceElement>() {
    public PsiJavaCodeReferenceElement[] newPsiElementArray(int length) {
      return length == 0 ? PsiJavaCodeReferenceElement.EMPTY_ARRAY : new PsiJavaCodeReferenceElement[length];
    }
  };

  PsiElementArrayConstructor<PsiStatement> PSI_STATEMENT_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiStatement>() {
    public PsiStatement[] newPsiElementArray(int length) {
      return length == 0 ? PsiStatement.EMPTY_ARRAY : new PsiStatement[length];
    }
  };

  PsiElementArrayConstructor<PsiExpression> PSI_EXPRESSION_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiExpression>() {
    public PsiExpression[] newPsiElementArray(int length) {
      return length == 0 ? PsiExpression.EMPTY_ARRAY : new PsiExpression[length];
    }
  };

  PsiElementArrayConstructor<PsiImportStatement> PSI_IMPORT_STATEMENT_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiImportStatement>() {
    public PsiImportStatement[] newPsiElementArray(int length) {
      return length == 0 ? PsiImportStatement.EMPTY_ARRAY : new PsiImportStatement[length];
    }
  };

  PsiElementArrayConstructor<PsiImportStaticStatement> PSI_IMPORT_STATIC_STATEMENT_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiImportStaticStatement>() {
    public PsiImportStaticStatement[] newPsiElementArray(int length) {
      return length == 0 ? PsiImportStaticStatement.EMPTY_ARRAY : new PsiImportStaticStatement[length];
    }
  };


  PsiElementArrayConstructor<PsiImportStatementBase> PSI_IMPORT_STATEMENT_BASE_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiImportStatementBase>() {
    public PsiImportStatementBase[] newPsiElementArray(int length) {
      return length == 0 ? PsiImportStatementBase.EMPTY_ARRAY : new PsiImportStatementBase[length];
    }
  };

  PsiElementArrayConstructor<PsiAnnotationMemberValue> PSI_ANNOTATION_MEMBER_VALUE_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiAnnotationMemberValue>() {
    public PsiAnnotationMemberValue[] newPsiElementArray(int length) {
      return length == 0 ? PsiAnnotationMemberValue.EMPTY_ARRAY : new PsiAnnotationMemberValue[length];
    }
  };

  PsiElementArrayConstructor<PsiNameValuePair> PSI_NAME_VALUE_PAIR_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiNameValuePair>() {
    public PsiNameValuePair[] newPsiElementArray(int length) {
      return length == 0 ? PsiNameValuePair.EMPTY_ARRAY : new PsiNameValuePair[length];
    }
  };

  PsiElementArrayConstructor<PsiAnnotation> PSI_ANNOTATION_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiAnnotation>() {
    public PsiAnnotation[] newPsiElementArray(int length) {
      return length == 0 ? PsiAnnotation.EMPTY_ARRAY : new PsiAnnotation[length];
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
