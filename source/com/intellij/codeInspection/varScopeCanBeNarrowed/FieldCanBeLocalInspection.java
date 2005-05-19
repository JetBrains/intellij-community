package com.intellij.codeInspection.varScopeCanBeNarrowed;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.containers.HashSet;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author ven
 */
public class FieldCanBeLocalInspection extends BaseLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection");

  public static final String SHORT_NAME = "FieldCanBeLocal";

  public String getGroupDisplayName() {
    return GROUP_LOCAL_CODE_ANALYSIS;
  }

  public String getDisplayName() {
    return "Field can be local";
  }

  public String getShortName() {
    return SHORT_NAME;
  }

  public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    PsiManager psiManager = aClass.getManager();
    final Set<PsiField> candidates = new LinkedHashSet<PsiField>();
    final PsiClass topLevelClass = PsiUtil.getTopLevelClass(aClass);
    if (topLevelClass == null) return null;
    final PsiField[] fields = aClass.getFields();
    NextField:
    for (PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
        final PsiReference[] refs = psiManager.getSearchHelper().findReferences(field, GlobalSearchScope.allScope(psiManager.getProject()),
                                                                                false);
        for (PsiReference ref : refs) {
          PsiElement element = ref.getElement();
          while (element != null) {
            if (element instanceof PsiMethod) {
              candidates.add(field);
              continue NextField;
            }
            element = PsiTreeUtil.getParentOfType(element, PsiMember.class);
          }
          continue NextField;
        }
      }
    }
    topLevelClass.accept(new PsiRecursiveElementVisitor() {
      public void visitElement(PsiElement element) {
        if (candidates.size() > 0) super.visitElement(element);
      }

      public void visitMethod(PsiMethod method) {
        super.visitMethod(method);

        final PsiCodeBlock body = method.getBody();
        if (body != null) {
          try {
            final ControlFlow controlFlow = ControlFlowFactory.getControlFlow(body, AllVariablesControlFlowPolicy.getInstance());
            final List<PsiReferenceExpression> readBeforeWrite = ControlFlowUtil.getReadBeforeWrite(controlFlow);
            for (Iterator<PsiReferenceExpression> iterator = readBeforeWrite.iterator(); iterator.hasNext();) {
              final PsiElement resolved = iterator.next().resolve();
              if (resolved instanceof PsiField) {
                final PsiField field = (PsiField)resolved;
                candidates.remove(field);
              }
            }
          }
          catch (AnalysisCanceledException e) {
            candidates.clear();
          }
        }
      }
    });

    if (candidates.isEmpty()) return null;
    ProblemDescriptor[] result = new ProblemDescriptor[candidates.size()];
    int i = 0;
    for (Iterator<PsiField> iterator = candidates.iterator(); iterator.hasNext(); i++) {
      PsiField field = iterator.next();
      final String message = "Field can be converted to one or more local variables.";
      result[i] = manager.createProblemDescriptor(field, message, new MyQuickFix(field), ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }
    return result;
  }

  private static class MyQuickFix implements LocalQuickFix {
    private PsiField myField;

    public MyQuickFix(final PsiField field) {
      myField = field;
    }

    public String getName() {
      return "Convert to local";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
      PsiManager manager = PsiManager.getInstance(project);
      PsiSearchHelper helper = manager.getSearchHelper();
      Set<PsiMethod> methodSet = new HashSet<PsiMethod>();
      final PsiReference[] allRefs = helper.findReferences(myField, GlobalSearchScope.allScope(project), false);
      for (PsiReference ref : allRefs) {
        if (ref instanceof PsiReferenceExpression) {
          final PsiMethod method = PsiTreeUtil.getParentOfType((PsiReferenceExpression)ref, PsiMethod.class);
          LOG.assertTrue(method != null);
          methodSet.add(method);
        }
      }

      PsiElement newCaretPosition = null;
      for (PsiMethod method : methodSet) {
        final PsiReference[] refs = helper.findReferences(myField, new LocalSearchScope(method), true);
        LOG.assertTrue(refs.length > 0);
        PsiCodeBlock anchorBlock = findAnchorBlock(refs);
        LOG.assertTrue(anchorBlock != null);
        final PsiElementFactory elementFactory = manager.getElementFactory();
        final CodeStyleManager styleManager = manager.getCodeStyleManager();
        final String propertyName = styleManager.variableNameToPropertyName(myField.getName(), VariableKind.FIELD);
        String localName = styleManager.propertyNameToVariableName(propertyName, VariableKind.LOCAL_VARIABLE);
        localName = RefactoringUtil.suggestUniqueVariableName(localName, anchorBlock, myField);
        try {
          final PsiDeclarationStatement decl = elementFactory.createVariableDeclarationStatement(localName, myField.getType(), null);
          final PsiElement anchor = getAnchorElement(anchorBlock, refs);
          final PsiElement newVariable = anchorBlock.addAfter(decl, anchor);
          if (newCaretPosition == null) {
            newCaretPosition = newVariable;
          }
          final PsiReferenceExpression refExpr = (PsiReferenceExpression)elementFactory.createExpressionFromText(localName, null);
          for (PsiReference ref : refs) {
            if (ref instanceof PsiReferenceExpression) {
              ((PsiReferenceExpression)ref).replace(refExpr);
            }
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      if (newCaretPosition != null) {
        final PsiFile psiFile = myField.getContainingFile();
        final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor != null && IJSwingUtilities.hasFocus(editor.getComponent())) {
          final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
          if (file == psiFile) {
            editor.getCaretModel().moveToOffset(newCaretPosition.getTextOffset());
            editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          }
        }
      }

      try {
        myField.normalizeDeclaration();
        myField.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }

    }

    private static PsiElement getAnchorElement(final PsiCodeBlock anchorBlock, final PsiReference[] refs) {
      PsiElement firstElement = null;
      for (PsiReference reference : refs) {
        final PsiElement element = reference.getElement();
        if (firstElement == null || firstElement.getTextRange().getStartOffset() > element.getTextRange().getStartOffset()) {
          firstElement = element;
        }
      }
      LOG.assertTrue(firstElement != null);
      while (firstElement.getParent() != anchorBlock) {
        firstElement = firstElement.getParent();
      }

      return PsiTreeUtil.skipSiblingsBackward(firstElement, new Class[]{PsiWhiteSpace.class, PsiComment.class});
    }

    private static PsiCodeBlock findAnchorBlock(final PsiReference[] refs) {
      PsiCodeBlock result = null;
      for (PsiReference psiReference : refs) {
        final PsiElement element = psiReference.getElement();
        PsiCodeBlock block = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
        if (result == null) {
          result = block;
        }
        else {
          final PsiElement commonParent = PsiTreeUtil.findCommonParent(result, block);
          result = PsiTreeUtil.getParentOfType(commonParent, PsiCodeBlock.class, false);
        }
      }
      return result;
    }
  }
}
