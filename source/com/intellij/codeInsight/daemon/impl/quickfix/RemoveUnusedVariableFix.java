package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RemoveUnusedVariableFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableFix");
  private final PsiVariable myVariable;

  public RemoveUnusedVariableFix(PsiVariable variable) {
    myVariable = variable;
  }

  public String getText() {
    final String text = MessageFormat.format("Remove {0} ''{1}''",
                                             new Object[]{
                                               myVariable instanceof PsiField ? "field" : "variable",
                                               myVariable.getName(),
                                             });
    return text;
  }

  public String getFamilyName() {
    return "Remove unused variable";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return
      myVariable != null
      && myVariable.isValid()
      && myVariable.getManager().isInProject(myVariable)
      ;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myVariable.getContainingFile())) return;
    removeVariableAndReferencingStatements(myVariable, editor);
  }

  private static void deleteReferences(PsiVariable variable, List<PsiElement> references, int mode)
    throws IncorrectOperationException {
    for (int i = 0; i < references.size(); i++) {
      PsiElement expression = references.get(i);

      processUsage(expression, variable, null, mode);
    }
  }

  private static void collectReferences(final PsiElement context, final PsiVariable variable, final List<PsiElement> references) {
    context.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.resolve() == variable) references.add(expression);
        super.visitReferenceExpression(expression);
      }
    });
  }

  private static void removeVariableAndReferencingStatements(final PsiVariable variable, Editor editor) {
    final List<PsiElement> references = new ArrayList<PsiElement>();
    final List<PsiElement> sideEffects = new ArrayList<PsiElement>();
    final boolean[] canCopeWithSideEffects = {true};
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          final PsiElement context = variable instanceof PsiField
                                     ? ((PsiField)variable).getContainingClass()
                                     : PsiUtil.getVariableCodeBlock(variable, null);
          collectReferences(context, variable, references);
          // do not forget to delete variable declaration
          references.add(variable);
          // check for side effects
          for (int i = 0; i < references.size(); i++) {
            PsiElement element = references.get(i);
            canCopeWithSideEffects[0] &=
            processUsage(element, variable, sideEffects, SideEffectWarningDialog.CANCEL);
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });

    // 0 - cancel, 1 - make statements from side effect expressions, 2 - delete all
    final int ret = showSideEffectsWarning(sideEffects, variable, editor, canCopeWithSideEffects[0]);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          deleteReferences(variable, references, ret);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
  }

  private static int showSideEffectsWarning(List<PsiElement> sideEffects,
                                            PsiVariable variable,
                                            Editor editor,
                                            boolean canCopeWithSideEffects) {
    if (sideEffects.size() == 0) return SideEffectWarningDialog.DELETE_ALL;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return canCopeWithSideEffects
             ? SideEffectWarningDialog.MAKE_STATEMENT
             : SideEffectWarningDialog.DELETE_ALL;
    }
    final Project project = variable.getProject();
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    final PsiElement[] elements = sideEffects.toArray(new PsiElement[sideEffects.size()]);
    EditorColorsManager manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    highlightManager.addOccurrenceHighlights(editor, elements, attributes, true, null);

    final SideEffectWarningDialog dialog = new SideEffectWarningDialog(project, false, variable, sideEffects.get(0).getText(),
                                                                       canCopeWithSideEffects);
    dialog.show();
    return dialog.getExitCode();
  }

  /**
   *
   * @param element
   * @param variable
   * @param sideEffects if null, delete usages, otherwise collect side effects
   * @return true if there are at least one unrecoverable side effect found
   * @throws IncorrectOperationException
   */
  private static boolean processUsage(PsiElement element, PsiVariable variable, List<PsiElement> sideEffects, int deleteMode)
    throws IncorrectOperationException {
    if (!element.isValid()) return true;
    final PsiElementFactory factory = variable.getManager().getElementFactory();
    while (element != null) {
      if (element instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression expression = (PsiAssignmentExpression)element;
        final PsiExpression lExpression = expression.getLExpression();
        // there should not be read access to the variable, otherwise it is not unused
        LOG.assertTrue(
          lExpression instanceof PsiReferenceExpression &&
          variable == ((PsiReferenceExpression)lExpression).resolve());
        PsiExpression rExpression = expression.getRExpression();
        rExpression = PsiUtil.deparenthesizeExpression(rExpression);
        if (rExpression == null) return true;
        // replace assignment with expression and resimplify
        final boolean sideEffectFound = checkSideEffects(rExpression, variable, sideEffects);
        if (!(element.getParent() instanceof PsiExpressionStatement) || PsiUtil.isStatement(rExpression)) {
          if (deleteMode == SideEffectWarningDialog.MAKE_STATEMENT ||
              (deleteMode == SideEffectWarningDialog.DELETE_ALL &&
               !(element.getParent() instanceof PsiExpressionStatement))) {
            element = replaceElementWithExpression(rExpression, factory, element);
            while (element.getParent() instanceof PsiParenthesizedExpression) {
              element = element.getParent().replace(element);
            }
            List<PsiElement> references = new ArrayList<PsiElement>();
            collectReferences(element, variable, references);
            deleteReferences(variable, references, deleteMode);
          }
          else if (deleteMode == SideEffectWarningDialog.DELETE_ALL) {
            deleteWholeStatement(element, factory);
          }
          return true;
        }
        else {
          if (deleteMode != SideEffectWarningDialog.CANCEL) {
            deleteWholeStatement(element, factory);
          }
          return !sideEffectFound;
        }
      }
      else if (element instanceof PsiExpressionStatement && deleteMode != SideEffectWarningDialog.CANCEL) {
        element.delete();
        break;
      }
      else if (element instanceof PsiVariable && element == variable) {
        PsiExpression expression = variable.getInitializer();
        if (expression != null) {
          expression = PsiUtil.deparenthesizeExpression(expression);
        }
        final boolean sideEffectsFound = checkSideEffects(expression, variable, sideEffects);
        if (expression != null && PsiUtil.isStatement(expression) && variable instanceof PsiLocalVariable
            &&
            !(variable.getParent() instanceof PsiDeclarationStatement &&
              ((PsiDeclarationStatement)variable.getParent()).getDeclaredElements().length > 1)) {
          if (deleteMode == SideEffectWarningDialog.MAKE_STATEMENT) {
            element = element.replace(createStatementIfNeeded(expression, factory, element));
            List<PsiElement> references = new ArrayList<PsiElement>();
            collectReferences(element, variable, references);
            deleteReferences(variable, references, deleteMode);
          }
          else if (deleteMode == SideEffectWarningDialog.DELETE_ALL) {
            element.delete();
          }
          return true;
        }
        else {
          if (deleteMode != SideEffectWarningDialog.CANCEL) {
            element.delete();
          }
          return !sideEffectsFound;
        }
      }
      element = element.getParent();
    }
    return true;
  }

  private static void deleteWholeStatement(PsiElement element, final PsiElementFactory factory)
    throws IncorrectOperationException {
    // just delete it altogether
    if (element.getParent() instanceof PsiExpressionStatement) {
      final PsiExpressionStatement parent = (PsiExpressionStatement)element.getParent();
      if (parent.getParent() instanceof PsiCodeBlock) {
        parent.delete();
      }
      else {
        // replace with empty statement (to handle with 'if (..) i=0;' )
        parent.replace(createStatementIfNeeded(null, factory, element));
      }
    }
    else {
      element.delete();
    }
  }

  private static PsiElement createStatementIfNeeded(PsiExpression expression,
                                                    PsiElementFactory factory,
                                                    PsiElement element) throws IncorrectOperationException {
    // if element used in expression, subexpression will do
    if (!(element.getParent() instanceof PsiExpressionStatement) &&
        !(element.getParent() instanceof PsiDeclarationStatement)) {
      return expression;
    }
    return factory.createStatementFromText((expression == null ? "" : expression.getText()) + ";", null);
  }

  private static PsiElement replaceElementWithExpression(PsiExpression expression,
                                                         PsiElementFactory factory,
                                                         PsiElement element) throws IncorrectOperationException {
    PsiElement elementToReplace = element;
    PsiElement expressionToReplaceWith = expression;
    if (element.getParent() instanceof PsiExpressionStatement) {
      elementToReplace = element.getParent();
      expressionToReplaceWith =
      factory.createStatementFromText((expression == null ? "" : expression.getText()) + ";", null);
    }
    else if (element.getParent() instanceof PsiDeclarationStatement) {
      expressionToReplaceWith =
      factory.createStatementFromText((expression == null ? "" : expression.getText()) + ";", null);
    }
    return elementToReplace.replace(expressionToReplaceWith);
  }

    private static boolean checkSideEffects(PsiElement element, PsiVariable variable, List<PsiElement> sideEffects) {
    if (sideEffects == null || element == null) return false;
    if (element instanceof PsiMethodCallExpression) {
      sideEffects.add(element);
      return true;
    }
    if (element instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)element;
      if (newExpression.getArrayDimensions().length == 0
          && newExpression.getArrayInitializer() == null
          && !isSideEffectFreeConstructor(newExpression)) {
        sideEffects.add(element);
        return true;
      }
    }
    if (element instanceof PsiAssignmentExpression
        && !(((PsiAssignmentExpression)element).getLExpression() instanceof PsiReferenceExpression
             && ((PsiReferenceExpression)((PsiAssignmentExpression)element).getLExpression()).resolve() == variable)) {
      sideEffects.add(element);
      return true;
    }
    final PsiElement[] children = element.getChildren();

    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];
      checkSideEffects(child, variable, sideEffects);
    }
    return sideEffects.size() > 0;
  }

  private static final Set<String> ourSideEffectFreeClasses = new THashSet<String>() {
    {
      add(java.lang.Object.class.getName());
      add(java.lang.Short.class.getName());
      add(java.lang.Character.class.getName());
      add(java.lang.Byte.class.getName());
      add(java.lang.Integer.class.getName());
      add(java.lang.Long.class.getName());
      add(java.lang.Float.class.getName());
      add(java.lang.Double.class.getName());
      add(java.lang.String.class.getName());
      add(java.lang.StringBuffer.class.getName());
      add(java.lang.Boolean.class.getName());

      add(java.util.ArrayList.class.getName());
      add(java.util.Date.class.getName());
      add(java.util.HashMap.class.getName());
      add(java.util.HashSet.class.getName());
      add(java.util.Hashtable.class.getName());
      add(java.util.LinkedHashMap.class.getName());
      add(java.util.LinkedHashSet.class.getName());
      add(java.util.LinkedList.class.getName());
      add(java.util.Stack.class.getName());
      add(java.util.TreeMap.class.getName());
      add(java.util.TreeSet.class.getName());
      add(java.util.Vector.class.getName());
      add(java.util.WeakHashMap.class.getName());

    }
  };

  private static boolean isSideEffectFreeConstructor(PsiNewExpression newExpression) {
    final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    final PsiClass aClass = classReference == null ? null : (PsiClass)classReference.resolve();
    final String qualifiedName = aClass == null ? null : aClass.getQualifiedName();
    if (qualifiedName == null) return false;
    if (ourSideEffectFreeClasses.contains(qualifiedName)) return true;

    final PsiFile file = aClass.getContainingFile();
    final PsiDirectory directory = file.getContainingDirectory();
    final PsiPackage classPackage = directory.getPackage();
    String packageName = classPackage.getQualifiedName();

    // all Throwable descendants from java.lang are side effects free
    if ("java.lang".equals(packageName) || "java.io".equals(packageName)) {
      PsiClass throwableClass = aClass.getManager().findClass("java.lang.Throwable", aClass.getResolveScope());
      if (throwableClass != null && InheritanceUtil.isInheritorOrSelf(aClass, throwableClass, true)) {
        return true;
      }
    }
    return false;
  }

  public boolean startInWriteAction() {
    return false;
  }


}
