/*
 * User: anna
 * Date: 26-Aug-2009
 */
package com.intellij.refactoring.typeMigration.intentions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.TypeMigrationReplacementUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.refactoring.typeMigration.rules.ThreadLocalConversionRule;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class ConvertFieldToThreadLocalIntention extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#" + ConvertFieldToThreadLocalIntention.class.getName());


  @NotNull
  public String getText() {
    return "Convert to ThreadLocal";
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @Nullable PsiElement element) {
    final PsiField psiField = PsiTreeUtil.getParentOfType(element, PsiField.class);
    if (psiField == null) return false;
    if (psiField.getTypeElement() == null) return false;
    final PsiType fieldType = psiField.getType();
    final PsiClass fieldTypeClass = PsiUtil.resolveClassInType(fieldType);
    if (fieldType instanceof PsiPrimitiveType) return true;
    return fieldTypeClass != null && !Comparing.strEqual(fieldTypeClass.getQualifiedName(), ThreadLocal.class.getName());
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;

    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiField psiField = PsiTreeUtil.getParentOfType(element, PsiField.class);
    LOG.assertTrue(psiField != null);
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    final PsiType fromType = psiField.getType();


    final PsiClass threadLocalClass = psiFacade.findClass(ThreadLocal.class.getName(), GlobalSearchScope.allScope(project));
    if (threadLocalClass == null) {//show warning
      return;
    }
    final HashMap<PsiTypeParameter, PsiType> substitutor = new HashMap<PsiTypeParameter, PsiType>();
    final PsiTypeParameter[] typeParameters = threadLocalClass.getTypeParameters();
    if (typeParameters.length == 1) {
      substitutor.put(typeParameters[0], fromType instanceof PsiPrimitiveType ? ((PsiPrimitiveType)fromType).getBoxedType(file) : fromType);
    }
    final PsiClassType toType = elementFactory.createType(threadLocalClass, elementFactory.createSubstitutor(substitutor));

    try {
      psiField.getTypeElement().replace(elementFactory.createTypeElement(toType));


      final TypeMigrationRules rules = new TypeMigrationRules(fromType);
      rules.setMigrationRootType(toType);
      rules.setBoundScope(GlobalSearchScope.fileScope(file));
      final TypeMigrationLabeler labeler = new TypeMigrationLabeler(rules);
      labeler.getMigratedUsages(psiField, false);
      for (PsiReference reference : ReferencesSearch.search(psiField)) {
        PsiElement psiElement = reference.getElement();
        if (psiElement instanceof PsiExpression) {
          if (psiElement.getParent() instanceof PsiExpression) {
            psiElement = psiElement.getParent();
          }
          final TypeConversionDescriptor directConversion = ThreadLocalConversionRule.findDirectConversion(psiElement, toType, fromType, labeler);
          if (directConversion != null) {
            TypeMigrationReplacementUtil.replaceExpression((PsiExpression)psiElement, project, directConversion);
          }
        }
      }

      final PsiExpression initializer = psiField.getInitializer();
      if (initializer != null) {
        final String boxedTypeName = fromType instanceof PsiPrimitiveType ? ((PsiPrimitiveType)fromType).getBoxedTypeName() : fromType.getCanonicalText();
        psiField.setInitializer(elementFactory.createExpressionFromText("new " +
                                                                        toType.getCanonicalText() +
                                                                        "() {\n" +
                                                                        "@Override \n" +
                                                                        "protected " +
                                                                        boxedTypeName +
                                                                        " initialValue() {\n" +
                                                                        "  return " +
                                                                        (PsiUtil.isLanguageLevel5OrHigher(psiField)
                                                                         ? initializer.getText()
                                                                         : (fromType instanceof PsiPrimitiveType
                                                                            ? "new " + ((PsiPrimitiveType)fromType).getBoxedTypeName() + "(" + initializer.getText() + ")"
                                                                            : initializer.getText())) +
                                                                        ";\n" +
                                                                        "}\n" +
                                                                        "}", psiField));
        CodeStyleManager.getInstance(project).reformat(psiField);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}