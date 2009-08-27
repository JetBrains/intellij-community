/*
 * User: anna
 * Date: 04-Apr-2008
 */
package com.intellij.refactoring.typeMigration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.Replacer;
import com.intellij.util.IncorrectOperationException;

import java.util.Map;

public class TypeMigrationReplacementUtil {
  public static final Logger LOG = Logger.getInstance("#" + TypeMigrationReplacementUtil.class.getName());

  private TypeMigrationReplacementUtil() {
  }

  public static void replaceExpression(PsiExpression expression, final Project project, Object conversion) {
    if (conversion instanceof TypeConversionDescriptor) {
      final TypeConversionDescriptor descriptor = (TypeConversionDescriptor)conversion;
      if (descriptor.getStringToReplace() == null) {
        conversion = descriptor.getReplaceByString();
      } else {
        if (descriptor.getExpression() != null) expression = descriptor.getExpression();
        final ReplaceOptions options = new ReplaceOptions();
        options.setMatchOptions(new MatchOptions());
        final Replacer replacer = new Replacer(project, null);
        try {
          conversion = replacer.testReplace(expression.getText(), descriptor.getStringToReplace(), descriptor.getReplaceByString(), options);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
          return;
        }
      }
    }
    if (conversion instanceof String) {
      String replacement = (String)conversion;
      if (Comparing.strEqual("$", replacement)) return; //optimization
      try {
        expression.replace(
            JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(replacement, expression));
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    else if (expression instanceof PsiReferenceExpression) {
      final PsiElement resolved = ((PsiReferenceExpression)expression).resolve();
      final PsiMember replacer = ((PsiMember)conversion);
      final String method = ((PsiMember)resolved).getName();
      final String ref = expression.getText();
      final String newref = ref.substring(0, ref.lastIndexOf(method)) + replacer.getName();

      if (conversion instanceof PsiMethod) {
        if (resolved instanceof PsiMethod) {
          try {
            expression.replace(
                JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(newref, expression));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
        else {
          try {
            expression.replace(JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(
                newref + "()", expression));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
      else if (conversion instanceof PsiField) {
        if (resolved instanceof PsiField) {
          try {
            expression.replace(
                JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(newref, expression));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
        else {
          final PsiElement parent = Util.getEssentialParent(expression);

          if (parent instanceof PsiMethodCallExpression) {
            try {
              parent.replace(
                  JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(newref, expression));
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }
    }
  }

  static void migratePsiMemeberType(final PsiElement element, final Project project, final PsiType migratedType) {
    try {
      final PsiTypeElement typeElement =
          JavaPsiFacade.getInstance(project).getElementFactory().createTypeElement(migratedType);
      if (element instanceof PsiMethod) {
        final PsiTypeElement returnTypeElement = ((PsiMethod)element).getReturnTypeElement();
        if (returnTypeElement != null) {
          returnTypeElement.replace(typeElement);
        }
      }
      else if (element instanceof PsiVariable) {
        final PsiTypeElement varTypeElement = ((PsiVariable)element).getTypeElement();
        if (varTypeElement != null) {
          varTypeElement.replace(typeElement);
        }
      }
      else {
        LOG.error("Must not happen: " + element.getClass().getName());
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  static void replaceNewExpressionType(final Project project, final PsiExpression expression, final Map.Entry<TypeMigrationUsageInfo, PsiType> info) {
    final PsiType changeType = info.getValue();
    if (changeType != null) {
      final String image = expression.getText();
      PsiType type = expression.getType().getDeepComponentType();

      try {
        final String replacement =
            image.replaceFirst(type.getPresentableText(), changeType.getDeepComponentType().getPresentableText());
        expression.replace(
            JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(replacement, expression));
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }
}