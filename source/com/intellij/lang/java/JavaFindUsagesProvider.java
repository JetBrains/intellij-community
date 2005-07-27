package com.intellij.lang.java;

import com.intellij.find.impl.HelpID;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.psi.*;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.impl.search.ThrowSearchUtil;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.jsp.WebDirectoryElement;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.usageView.UsageViewUtil;

/**
 * @author ven
 */
public class JavaFindUsagesProvider implements FindUsagesProvider {
  public boolean canFindUsagesFor(PsiElement element) {
    if (element instanceof PsiDirectory) {
      PsiPackage psiPackage = ((PsiDirectory)element).getPackage();
      if (psiPackage == null) {
        return false;
      }
      return psiPackage.getQualifiedName().length() != 0;
    }

    return element instanceof PsiClass ||
           element instanceof PsiVariable ||
           element instanceof PsiMethod ||
           element instanceof PsiPackage ||
           element instanceof PsiLabeledStatement ||
           ThrowSearchUtil.isSearchable(element);
  }

  public String getHelpId(PsiElement element) {
    if (element instanceof PsiPackage) {
      return HelpID.FIND_PACKAGE_USAGES;
    }
    else if (element instanceof PsiClass) {
      return HelpID.FIND_CLASS_USAGES;
    }
    else if (element instanceof PsiMethod) {
      return HelpID.FIND_METHOD_USAGES;
    }
    else if (ThrowSearchUtil.isSearchable(element)) {
      return HelpID.FIND_THROW_USAGES;
    }
    else if (element instanceof PsiField) {
      return HelpID.FIND_FIELD_USAGES;
    }
    else if (element instanceof PsiLocalVariable) {
      return HelpID.FIND_VARIABLE_USAGES;
    }
    else if (element instanceof PsiParameter) {
      return HelpID.FIND_PARAMETER_USAGES;
    }

    return null;
  }

  public String getType(PsiElement element) {
    if (element instanceof PsiDirectory) {
      return "directory";
    }
    if (element instanceof WebDirectoryElement) {
      return "web directory";
    }
    if (element instanceof PsiFile) {
      return "file";
    }
    if (ThrowSearchUtil.isSearchable(element)) {
      return "exception";
    }
    if (element instanceof PsiPackage) {
      return "package";
    }
    if (element instanceof PsiLabeledStatement) {
      return "label";
    }
    if (element instanceof PsiClass) {
      if (((PsiClass)element).isAnnotationType()) {
        return "@interface";
      } else if (((PsiClass)element).isEnum()) {
        return "enum";
      } else if (((PsiClass)element).isInterface()) {
        return "interface";
      }
      return "class";
    }
    if (element instanceof PsiField) {
      return "field";
    }
    if (element instanceof PsiParameter) {
      return "parameter";
    }
    if (element instanceof PsiLocalVariable) {
      return "variable";
    }
    if (element instanceof PsiMethod) {
      final PsiMethod psiMethod = (PsiMethod)element;
      final boolean isConstructor = psiMethod.isConstructor();
      if (isConstructor) {
        return "constructor";
      }
      else {
        return "method";
      }
    }
    return "";
  }

  public String getDescriptiveName(final PsiElement element) {
    if (ThrowSearchUtil.isSearchable(element)) {
      return ThrowSearchUtil.getSearchableTypeName(element);
    }
    if (element instanceof PsiDirectory) {
      return UsageViewUtil.getPackageName((PsiDirectory)element, false);
    }
    else if (element instanceof PsiPackage) {
      return UsageViewUtil.getPackageName((PsiPackage)element);
    }
    else if (element instanceof PsiFile) {
      return ((PsiFile)element).getVirtualFile().getPresentableUrl();
    }
    else if (element instanceof PsiLabeledStatement) {
      return ((PsiLabeledStatement)element).getLabelIdentifier().getText();
    }
    else if (element instanceof PsiClass) {
      if (element instanceof PsiAnonymousClass) {
        return "anonymous class";
      }
      else {
        final PsiClass aClass = (PsiClass)element;
        String qName =  aClass.getQualifiedName();
        return qName == null ? aClass.getName() : qName;
      }
    }
    else if (element instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)element;
      String formatted = PsiFormatUtil.formatMethod(psiMethod,
                                       PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                       PsiFormatUtil.SHOW_TYPE);
      PsiClass psiClass = psiMethod.getContainingClass();
      if (psiClass != null) {
        return formatted + getContainingClassDescription(psiClass);
      }

      return formatted;
    }
    else if (element instanceof PsiField) {
      PsiField psiField = (PsiField)element;
      String formatted = PsiFormatUtil.formatVariable(psiField, PsiFormatUtil.SHOW_NAME, PsiSubstitutor.EMPTY);
      PsiClass psiClass = psiField.getContainingClass();
      if (psiClass != null) {
        return formatted + getContainingClassDescription(psiClass);
      }

      return formatted;
    }
    else if (element instanceof PsiVariable) {
      return PsiFormatUtil.formatVariable((PsiVariable)element, PsiFormatUtil.SHOW_NAME, PsiSubstitutor.EMPTY);
    }

    return "";
  }

  private String getContainingClassDescription (PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      return " of anonymous class";
    }
    else {
      String className = aClass.getName();
      if (aClass.isInterface()) {
          return " of interface " + className;
      } else if (aClass.isEnum()) {
          return " of enum " + className;
      } else if (aClass.isAnnotationType()) {
          return " of annotation type " + className;
      }
      else {
        return " of class " + className;
      }
    }
  }

  public String getNodeText(PsiElement element, boolean useFullName) {
    if (element instanceof PsiDirectory) {
      return UsageViewUtil.getPackageName((PsiDirectory)element, false);
    }
    if (element instanceof PsiPackage) {
      return UsageViewUtil.getPackageName((PsiPackage)element);
    }
    if (element instanceof PsiFile) {
      return useFullName ? ((PsiFile)element).getVirtualFile().getPresentableUrl() : ((PsiFile)element).getName();
    }
    if (element instanceof PsiLabeledStatement) {
      return ((PsiLabeledStatement)element).getLabelIdentifier().getText();
    }
    if (ThrowSearchUtil.isSearchable(element)) {
      return ThrowSearchUtil.getSearchableTypeName(element);
    }

    if (element instanceof PsiClass) {
      String name = ((PsiClass)element).getQualifiedName();
      if (name == null || !useFullName) {
        name = ((PsiClass)element).getName();
      }
      return name;
    }
    if (element instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)element;
      if (useFullName) {
        String s = PsiFormatUtil.formatMethod((PsiMethod)element,
                                              PsiSubstitutor.EMPTY, PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_TYPE |
                                                                    PsiFormatUtil.SHOW_NAME |
                                                                    PsiFormatUtil.SHOW_PARAMETERS,
                                              PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_NAME);
        final PsiClass psiClass = psiMethod.getContainingClass();
        if (psiClass != null) {
          final String qName = psiClass.getQualifiedName();
          if (qName != null) {
            s = s + " of " + (psiClass.isInterface() ? "interface " : "class ") + qName;
          }
        }
        return s;
      }
      else {
        return PsiFormatUtil.formatMethod(psiMethod,
                                          PsiSubstitutor.EMPTY,
                                          PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                          PsiFormatUtil.SHOW_TYPE);
      }
    }
    else if (element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)((PsiParameter)element).getDeclarationScope();
      String s = PsiFormatUtil.formatVariable((PsiVariable)element,
                                              PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_NAME,
                                              PsiSubstitutor.EMPTY) + " of " +
                 PsiFormatUtil.formatMethod(method,
                                            PsiSubstitutor.EMPTY,
                                            PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                            PsiFormatUtil.SHOW_TYPE);

      final PsiClass psiClass = method.getContainingClass();
      if (psiClass != null && psiClass.getQualifiedName() != null) {
        s += " of " + (psiClass.isInterface() ? "interface " : "class ") + psiClass.getQualifiedName();
      }
      return s;
    }
    else if (element instanceof PsiField) {
      PsiField psiField = (PsiField)element;
      String s = PsiFormatUtil.formatVariable(psiField,
                                              PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_NAME,
                                              PsiSubstitutor.EMPTY);
      PsiClass psiClass = psiField.getContainingClass();
      if (psiClass != null) {
        String qName = psiClass.getQualifiedName();
        if (qName != null) {
          s = s + " of " + (psiClass.isInterface() ? "interface " : "class ") + qName;
        }
      }
      return s;
    }
    else if (element instanceof PsiVariable) {
      return PsiFormatUtil.formatVariable((PsiVariable)element,
                                          PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_NAME,
                                          PsiSubstitutor.EMPTY);
    }

    return "";
  }

  private static class Inner {
    private static final TokenSet COMMENT_BIT_SET = TokenSet.create(new IElementType[]{
      ElementType.DOC_COMMENT_DATA,
      ElementType.DOC_TAG_VALUE_TOKEN,
      ElementType.C_STYLE_COMMENT,
      ElementType.END_OF_LINE_COMMENT});
  }

  public boolean mayHaveReferences(IElementType token, final short searchContext) {
    return mayHaveReferencesImpl(token, searchContext);
  }

  public WordsScanner getWordsScanner() {
    return null;
  }

  public static boolean mayHaveReferencesImpl(final IElementType token, final short searchContext) {
    if ((searchContext & UsageSearchContext.IN_STRINGS) != 0 && token == ElementType.LITERAL_EXPRESSION) return true;
    if ((searchContext & UsageSearchContext.IN_COMMENTS) != 0 && Inner.COMMENT_BIT_SET.isInSet(token)) return true;
    if ((searchContext & UsageSearchContext.IN_CODE) != 0 && (token == ElementType.IDENTIFIER || token == ElementType.DOC_TAG_VALUE_TOKEN)) {
      return true;
    }
    // Java string literal to property file
    if ((searchContext & UsageSearchContext.IN_FOREIGN_LANGUAGES) != 0 && token == ElementType.LITERAL_EXPRESSION) return true;
    return false;
  }
}
