package com.intellij.psi.impl.source;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.impl.source.tree.ElementType;

public interface Constants extends ElementType {
  public static interface PsiElementArrayConstructor {
    PsiElement[] newPsiElementArray(int length);
  }

  public static final PsiElementArrayConstructor PSI_ELEMENT_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiElement[length] : PsiElement.EMPTY_ARRAY;
    }
  };

  public static final PsiElementArrayConstructor PSI_CLASS_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiClass[length] : PsiClass.EMPTY_ARRAY;
    }
  };

  public static final PsiElementArrayConstructor PSI_FIELD_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiField[length] : PsiField.EMPTY_ARRAY;
    }
  };

  public static final PsiElementArrayConstructor PSI_METHOD_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiMethod[length] : PsiMethod.EMPTY_ARRAY;
    }
  };

  public static final PsiElementArrayConstructor PSI_CLASS_INITIALIZER_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiClassInitializer[length] : PsiClassInitializer.EMPTY_ARRAY;
    }
  };

  public static final PsiElementArrayConstructor PSI_PARAMETER_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiParameter[length] : PsiParameter.EMPTY_ARRAY;
    }
  };

  public static final PsiElementArrayConstructor PSI_CATCH_SECTION_ARRAYS_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiCatchSection[length] : PsiCatchSection.EMPTY_ARRAY;
    }
  };

  public static final PsiElementArrayConstructor PSI_REFERENCE_ELEMENT_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiJavaCodeReferenceElement[length] : PsiJavaCodeReferenceElement.EMPTY_ARRAY;
    }
  };

  public static final PsiElementArrayConstructor PSI_STATEMENT_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiStatement[length] : PsiStatement.EMPTY_ARRAY;
    }
  };

  public static final PsiElementArrayConstructor PSI_EXPRESSION_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiExpression[length] : PsiExpression.EMPTY_ARRAY;
    }
  };

  public static final PsiElementArrayConstructor PSI_IMPORT_STATEMENT_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiImportStatement[length] : EMPTY;
    }

    private final PsiElement[] EMPTY = new PsiImportStatement[0];
  };

  public static final PsiElementArrayConstructor PSI_IMPORT_STATIC_STATEMENT_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiImportStaticStatement[length] : PsiImportStaticStatement.EMPTY_ARRAY;
    }
  };


  public static final PsiElementArrayConstructor PSI_IMPORT_STATEMENT_BASE_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiImportStatementBase[length] : PsiImportStatementBase.EMPTY_ARRAY;
    }
  };

  public static final PsiElementArrayConstructor PSI_ANNOTATION_MEMBER_VALUE_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiAnnotationMemberValue[length] : PsiAnnotationMemberValue.EMPTY_ARRAY;
    }
  };

  public static final PsiElementArrayConstructor PSI_NAME_VALUE_PAIR_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiNameValuePair[length] : PsiNameValuePair.EMPTY_ARRAY;
    }
  };

  public static final PsiElementArrayConstructor PSI_ANNOTATION_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return length != 0 ? new PsiAnnotation[length] : PsiAnnotation.EMPTY_ARRAY;
    }
  };

  TokenSet CLASS_BIT_SET = TokenSet.create(new IElementType[]{CLASS});
  TokenSet FIELD_BIT_SET = TokenSet.create(new IElementType[]{FIELD, ENUM_CONSTANT});
  TokenSet METHOD_BIT_SET = TokenSet.create(new IElementType[]{METHOD, ANNOTATION_METHOD});
  TokenSet CLASS_INITIALIZER_BIT_SET = TokenSet.create(new IElementType[]{CLASS_INITIALIZER});
  TokenSet PARAMETER_BIT_SET = TokenSet.create(new IElementType[]{PARAMETER});
  TokenSet CATCH_SECTION_BIT_SET = TokenSet.create(new IElementType[]{CATCH_SECTION});
  TokenSet JAVA_CODE_REFERENCE_BIT_SET = TokenSet.create(new IElementType[]{JAVA_CODE_REFERENCE});
  TokenSet NAME_VALUE_PAIR_BIT_SET = TokenSet.create(new IElementType[]{NAME_VALUE_PAIR});
  public static final TokenSet ANNOTATION_BIT_SET = TokenSet.create(new IElementType[]{ANNOTATION});
}
