package com.intellij.codeInspection.varScopeCanBeNarrowed;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import com.intellij.refactoring.util.RefactoringUtil;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author ven
 */
public class FieldCanBeLocalInspection extends BaseLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection");
  public String getGroupDisplayName() {
    return "Local Code Analysis";
  }

  public String getDisplayName() {
    return "Field can be local";
  }

  public String getShortName() {
    return "FieldCanBeLocal";
  }

  public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    PsiManager psiManager = aClass.getManager();
    final Set<PsiField> candidates = new LinkedHashSet<PsiField>();
    final PsiClass topLevelClass = PsiUtil.getTopLevelClass(aClass);
    if (topLevelClass == null) return null;
    final PsiField[] fields = aClass.getFields();
    NextField:
    for (int i = 0; i < fields.length; i++) {
      PsiField field = fields[i];
      if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
        final PsiReference[] refs = psiManager.getSearchHelper().findReferences(field, GlobalSearchScope.allScope(psiManager.getProject()), false);
        for (int j = 0; j < refs.length; j++) {
          PsiElement element = refs[j].getElement();
          while(element != null) {
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
                candidates.remove(resolved);
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

  class MyQuickFix implements LocalQuickFix {
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
      for (int i = 0; i < allRefs.length; i++) {
        PsiReference ref = allRefs[i];
        if (ref instanceof PsiReferenceExpression) {
          final PsiMethod method = PsiTreeUtil.getParentOfType(((PsiReferenceExpression)ref), PsiMethod.class);
          LOG.assertTrue(method != null);
          methodSet.add(method);
        }
      }

      for (Iterator<PsiMethod> iterator = methodSet.iterator(); iterator.hasNext();) {
        PsiMethod method = iterator.next();
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
          final PsiElement firstBodyElement = anchorBlock.getFirstBodyElement();
          LOG.assertTrue(firstBodyElement != null);
          anchorBlock.addBefore(decl, firstBodyElement);
          final PsiReferenceExpression refExpr = (PsiReferenceExpression)elementFactory.createExpressionFromText(localName, null);
          for (int j = 0; j < refs.length; j++) {
            PsiReference ref = refs[j];
            if (ref instanceof PsiReferenceExpression) {
              ((PsiReferenceExpression)ref).replace(refExpr);
            }
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      try {
        myField.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    private PsiCodeBlock findAnchorBlock(final PsiReference[] refs) {
      PsiCodeBlock result = null;
      for (int i = 0; i < refs.length; i++) {
        final PsiElement element = refs[i].getElement();
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
