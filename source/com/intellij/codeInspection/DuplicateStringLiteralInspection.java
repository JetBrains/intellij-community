package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.refactoring.util.occurences.BaseOccurenceManager;
import com.intellij.refactoring.util.occurences.OccurenceFilter;
import com.intellij.refactoring.util.occurences.OccurenceManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInsight.CodeInsightUtil;
import gnu.trove.THashSet;

import javax.swing.*;
import java.util.*;

public class DuplicateStringLiteralInspection extends BaseLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.DuplicateStringLiteralInspection");

  public DuplicateStringLiteralInspection() {
  }

  private static List<ProblemDescriptor> visitExpressionsUnder(PsiElement element, final InspectionManager manager) {
    if (element == null) return Collections.EMPTY_LIST;
    final List<ProblemDescriptor> allProblems = new ArrayList<ProblemDescriptor>();
    element.acceptChildren(new PsiRecursiveElementVisitor() {
      public void visitClass(PsiClass aClass) {
        // prevent double class checking
      }

      public void visitLiteralExpression(PsiLiteralExpression expression) {
        checkExpression(expression, manager, allProblems);
      }
    });
    return allProblems;
  }

  public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    final List<ProblemDescriptor> allProblems = visitExpressionsUnder(aClass, manager);
    return allProblems.size() == 0 ? null : allProblems.toArray(new ProblemDescriptor[allProblems.size()]);
  }

  public String getDisplayName() {
    return "Duplicate String Literal";
  }

  public String getGroupDisplayName() {
    return "Local Code Analysis";
  }

  public String getShortName() {
    return "DuplicateStringLiteralInspection";
  }

  private static void checkExpression(final PsiLiteralExpression originalExpression,
                                                   InspectionManager manager,
                                                   final List<ProblemDescriptor> allProblems) {
    if (!(originalExpression.getValue() instanceof String)) return;
    final GlobalSearchScope scope = GlobalSearchScope.projectScope(originalExpression.getProject());
    final String stringToFind = (String)originalExpression.getValue();
    final PsiSearchHelper searchHelper = originalExpression.getManager().getSearchHelper();
    final List<String> words = StringUtil.getWordsIn(stringToFind);
    if (words.size() == 0) return;
    // put longer strings first
    Collections.sort(words, new Comparator<String>() {
      public int compare(final String o1, final String o2) {
        return o2.length() - o1.length();
      }
    });

    Set<PsiFile> resultFiles = null;
    for (int i = 0; i < words.size(); i++) {
      String word = words.get(i);
      final Set<PsiFile> files = new THashSet<PsiFile>();
      searchHelper.processAllFilesWithWordInLiterals(word, scope, new PsiSearchHelper.FileSink() {
        public void foundFile(PsiFile file) {
          files.add(file);
        }
      });
      final boolean firstTime = i == 0;
      if (firstTime) {
        resultFiles = files;
      }
      else {
        resultFiles.retainAll(files);
      }
      if (resultFiles.size() == 0) return;
    }
    final List<PsiExpression> foundExpr = new ArrayList<PsiExpression>();
    for (Iterator<PsiFile> iterator = resultFiles.iterator(); iterator.hasNext();) {
      PsiFile file = iterator.next();
      file.accept(new PsiRecursiveElementVisitor() {
        public void visitLiteralExpression(PsiLiteralExpression expression) {
          if (expression != originalExpression && Comparing.equal(stringToFind, expression.getValue())) {
            foundExpr.add(expression);
          }
        }
      });
    }
    if (foundExpr.size() == 0) return;
    Set<PsiClass> classes = new THashSet<PsiClass>();
    for (int i = 0; i < foundExpr.size(); i++) {
      PsiElement aClass = foundExpr.get(i);
      do {
        aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
      }
      while (aClass instanceof PsiAnonymousClass || (aClass != null && ((PsiClass)aClass).getQualifiedName() == null));
      if (aClass == null) continue;
      classes.add((PsiClass)aClass);
    }
    if (classes.size() == 0) return;
    String msg = "<html><body>Duplicate string literal found in ";
    int i = 0;
    for (Iterator<PsiClass> iterator = classes.iterator(); iterator.hasNext(); i++) {
      final PsiClass aClass = iterator.next();
      if (i > 10) {
        msg += "<br>... (" + (classes.size() - i) + " more)";
        break;
      }
      msg += (i == 0 ? "" : ", ") + "<br>&nbsp;&nbsp;&nbsp;'<b>" + aClass.getQualifiedName() + "</b>'";
      if (aClass.getContainingFile() == originalExpression.getContainingFile()) {
        msg += " (in this file)";
      }
    }
    msg += "</body></html>";

    final PsiExpression[] expressions = foundExpr.toArray(new PsiExpression[foundExpr.size()+1]);
    expressions[foundExpr.size()] = originalExpression;
    final LocalQuickFix introduceQuickFix = new LocalQuickFix() {
      public String getName() {
        return IntroduceConstantHandler.REFACTORING_NAME;
      }

      public void applyFix(final Project project, ProblemDescriptor descriptor) {
        final IntroduceConstantHandler handler = new IntroduceConstantHandler() {
          protected OccurenceManager createOccurenceManager(PsiExpression selectedExpr, PsiClass parentClass) {
            final OccurenceFilter filter = new OccurenceFilter() {
              public boolean isOK(PsiExpression occurence) {
                return true;
              }
            };
            return new BaseOccurenceManager(filter) {
              protected PsiExpression[] defaultOccurences() {
                return expressions;
              }

              protected PsiExpression[] findOccurences() {
                return expressions;
              }
            };
          }
        };
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            handler.invoke(project, expressions, null);
          }
        });
      }
    };
    ProblemDescriptor problemDescriptor = manager.createProblemDescriptor(originalExpression, msg, introduceQuickFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    allProblems.add(problemDescriptor);

    Set<PsiField> constants = new THashSet<PsiField>();
    for (Iterator<PsiExpression> iterator = foundExpr.iterator(); iterator.hasNext();) {
      PsiExpression expression = iterator.next();
      if (expression.getParent() instanceof PsiField) {
        final PsiField field = (PsiField)expression.getParent();
        if (field.getInitializer() == expression && field.hasModifierProperty(PsiModifier.FINAL) && field.hasModifierProperty(PsiModifier.STATIC)) {
          constants.add(field);
          iterator.remove();
        }
      }
    }
    for (Iterator<PsiField> iterator = constants.iterator(); iterator.hasNext();) {
      final PsiField constant = iterator.next();
      final PsiClass containingClass = constant.getContainingClass();
      if (containingClass == null) continue;
      boolean isAccessible = PsiManager.getInstance(constant.getProject()).getResolveHelper() .isAccessible(constant, originalExpression, containingClass);
      if (!isAccessible && containingClass.getQualifiedName() == null) {
        continue;
      }
      final LocalQuickFix replaceQuickFix = new LocalQuickFix() {
        public String getName() {
          return "Replace with '"+PsiFormatUtil.formatVariable(constant, PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_FQ_NAME | PsiFormatUtil.SHOW_NAME,PsiSubstitutor.EMPTY)+"'";
        }

        public void applyFix(final Project project, ProblemDescriptor descriptor) {
          if (!CodeInsightUtil.prepareFileForWrite(originalExpression.getContainingFile())) return;
          try {
            final PsiReferenceExpression reference = createReferenceTo(constant, originalExpression);
            originalExpression.replace(reference);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      };
      problemDescriptor = manager.createProblemDescriptor(originalExpression, msg, replaceQuickFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      allProblems.add(problemDescriptor);
    }
  }

  private static PsiReferenceExpression createReferenceTo(final PsiField constant, final PsiLiteralExpression context) throws IncorrectOperationException {
    PsiReferenceExpression reference = (PsiReferenceExpression)constant.getManager().getElementFactory().createExpressionFromText(constant.getName(), context);
    if (reference.isReferenceTo(constant)) return reference;
    reference = (PsiReferenceExpression)constant.getManager().getElementFactory().createExpressionFromText("XXX."+constant.getName(), null);
    final PsiReferenceExpression classQualifier = (PsiReferenceExpression)reference.getQualifierExpression();
    classQualifier.replace(classQualifier.bindToElement(constant.getContainingClass()));

    if (reference.isReferenceTo(constant)) return reference;
    return null;
  }

  public boolean isEnabledByDefault() {
    return true;
  }
}
