package com.intellij.refactoring.inline;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

/**
 * @author ven
 */
class InlineConstantFieldProcessor extends BaseRefactoringProcessor implements InlineFieldDialog.Callback {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineConstantFieldProcessor");
  private PsiField myField;
  private InlineFieldDialog myDialog;
  private PsiReferenceExpression myRefExpr;
  private Editor myEditor;

  public InlineConstantFieldProcessor(PsiField field, Project project, PsiReferenceExpression ref, Editor editor) {
    super(project);
    myField = field;
    myRefExpr = ref;
    myEditor = editor;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    return new InlineViewDescriptor(myField, usages, refreshCommand);
  }

  protected UsageInfo[] findUsages() {
    PsiManager manager = myField.getManager();
    if (myDialog.isInlineThisOnly()) return new UsageInfo[]{new UsageInfo(myRefExpr)};

    PsiSearchHelper helper = manager.getSearchHelper();
    PsiReference[] refs = helper.findReferences(myField, GlobalSearchScope.projectScope(myProject), false);
    UsageInfo[] infos = new UsageInfo[refs.length];
    for (int i = 0; i < refs.length; i++) {
      PsiElement element = refs[i].getElement();
      if (element instanceof PsiReferenceExpression) {
        infos[i] = new UsageInfo(element);
      }
    }
    return infos;
  }

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == 1 && elements[0] instanceof PsiField);
    myField = (PsiField)elements[0];
  }

  protected void performRefactoring(UsageInfo[] usages) {
    int col = -1;
    int line = -1;
    if (myEditor != null) {
      col = myEditor.getCaretModel().getLogicalPosition().column;
      line = myEditor.getCaretModel().getLogicalPosition().line;
      LogicalPosition pos = new LogicalPosition(0, 0);
      myEditor.getCaretModel().moveToLogicalPosition(pos);
    }

    PsiExpression initializer = myField.getInitializer();
    LOG.assertTrue(initializer != null);

    PsiConstantEvaluationHelper evalHelper = myField.getManager().getConstantEvaluationHelper();
    initializer = normalize ((PsiExpression)initializer.copy());
    for (int i = 0; i < usages.length; i++) {
      PsiExpression initializer1 = initializer;
      PsiExpression expr = (PsiExpression)usages[i].getElement();
      while (expr.getParent() instanceof PsiArrayAccessExpression) {
        PsiArrayAccessExpression arrayAccess = ((PsiArrayAccessExpression)expr.getParent());
        Object value = evalHelper.computeConstantExpression(arrayAccess.getIndexExpression());
        if (value instanceof Integer) {
          int intValue = ((Integer)value).intValue();
          if (initializer1 instanceof PsiNewExpression) {
            PsiExpression[] arrayInitializers = ((PsiNewExpression)initializer1).getArrayInitializer().getInitializers();
            if (0 <= intValue && intValue < arrayInitializers.length) {
              expr = (PsiExpression)expr.getParent();
              initializer1 = normalize(arrayInitializers[intValue]);
              continue;
            }
          }
        }

        break;
      }

      try {
        myField.normalizeDeclaration();
        ChangeContextUtil.encodeContextInfo(initializer1, true);
        PsiElement element = expr.replace(initializer1);
        ChangeContextUtil.decodeContextInfo(element, null, null);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    if (!myDialog.isInlineThisOnly()) {
      try {
        myField.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    if (myEditor != null) {
      LogicalPosition pos = new LogicalPosition(line, col);
      myEditor.getCaretModel().moveToLogicalPosition(pos);
    }
  }

  private PsiExpression normalize(PsiExpression expression) {
    if (expression instanceof PsiArrayInitializerExpression) {
      PsiElementFactory factory = expression.getManager().getElementFactory();
      try {
        String typeString = expression.getType().getCanonicalText();
        PsiNewExpression result = (PsiNewExpression)factory.createExpressionFromText("new " + typeString + "{}", expression);
        result.getArrayInitializer().replace(expression);
        return result;
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return expression;
      }
    }

    return expression;
  }

  protected String getCommandName() {
    return "Inline field " + UsageViewUtil.getDescriptiveName(myField);
  }

  protected boolean preprocessUsages(UsageInfo[][] usages) {
    ArrayList<String> conflicts = new ArrayList<String>();

    ReferencedElementsCollector collector = new ReferencedElementsCollector();
    PsiExpression initializer = myField.getInitializer();
    LOG.assertTrue(initializer != null);
    initializer.accept(collector);
    HashSet<PsiMember> referencedWithVisibility = collector.myReferencedMembers;

    UsageInfo[] plainUsages = usages[0];
    PsiResolveHelper resolveHelper = myField.getManager().getResolveHelper();
    for (int i = 0; i < plainUsages.length; i++) {
      UsageInfo info = plainUsages[i];
      PsiElement element = info.getElement();
      LOG.assertTrue(element instanceof PsiReferenceExpression);
      if (isAccessedForWriting((PsiExpression)element)) {
        String message = ConflictsUtil.getDescription(myField, true) + " is used for writing in " +
                         ConflictsUtil.getDescription(ConflictsUtil.getContainer(element), true);
        conflicts.add(message);
      }

      for (Iterator<PsiMember> iterator = referencedWithVisibility.iterator(); iterator.hasNext();) {
        PsiMember member = iterator.next();
        if (!resolveHelper.isAccessible(member, element, null))  {
          String message = ConflictsUtil.getDescription(member, true) +
                           " will not be accessible from " +
                           ConflictsUtil.getDescription(ConflictsUtil.getContainer(element), true) +
                           " after inlining";
          conflicts.add(message);
        }
      }
    }

    if (myDialog != null && conflicts.size() > 0) {
      ConflictsDialog dialog = new ConflictsDialog(conflicts.toArray(new String[conflicts.size()]),
                                                   myProject);
      dialog.show();
      if (!dialog.isOK()) return false;
    }

    ToolWindowManager.getInstance(myProject).invokeLater(new Runnable() {
      public void run() {
        myDialog.close(DialogWrapper.CANCEL_EXIT_CODE);
      }
    });
    return true;
  }

  private boolean isAccessedForWriting (PsiExpression expr) {
    while(expr.getParent() instanceof PsiArrayAccessExpression) {
      expr = (PsiExpression)expr.getParent();
    }

    return PsiUtil.isAccessedForWriting(expr);
  }

  protected boolean isPreviewUsages(UsageInfo[] usages) {
    boolean toPreview = myDialog.isPreviewUsages();
    if (UsageViewUtil.hasReadOnlyUsages(usages)) {
      toPreview = true;
      WindowManager.getInstance().getStatusBar(myProject).setInfo("Occurrences found in read-only files");
    }
    return toPreview;
  }

  public void run(InlineFieldDialog dialog) {
    myDialog = dialog;
    this.run((Object)null);
  }
}
