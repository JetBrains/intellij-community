/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Nov 19, 2002
 * Time: 12:03:39 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowAnalyzer;
import com.intellij.psi.controlFlow.ControlFlowFactory;
import com.intellij.psi.controlFlow.LocalsOrMyInstanceFieldsControlFlowPolicy;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

public class DeferFinalAssignmentFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.DeferFinalAssignmentFix");

  private final PsiVariable variable;
  private final PsiReferenceExpression expression;

  public DeferFinalAssignmentFix(PsiVariable variable, PsiReferenceExpression expression) {
    this.variable = variable;
    this.expression = expression;
  }

  public String getFamilyName() {
    return "Defer final assignment with temp";
  }

  public String getText() {
    return "Defer assignment to '"+variable.getName()+"' using temp variable";
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(variable.getContainingFile())) return;

    if (variable instanceof PsiField) {
      deferField((PsiField)variable);
    }
    else {
      deferLocalVariable((PsiLocalVariable)variable);
    }
  }

  private void deferField(PsiField field) throws IncorrectOperationException {
    final PsiCodeBlock codeBlock = getEnclosingCodeBlock(field, expression);
    if (codeBlock == null) return;
    deferVariable(codeBlock, field, null);
  }

  private PsiCodeBlock getEnclosingCodeBlock(PsiField field, PsiElement element) {
    final PsiClass aClass = field.getContainingClass();
    if (aClass == null) return null;
    final PsiMethod[] constructors = aClass.getConstructors();
    for (int i = 0; i < constructors.length; i++) {
      PsiMethod constructor = constructors[i];
      final PsiCodeBlock body = constructor.getBody();
      if (body == null) continue;
      if (PsiTreeUtil.isAncestor(body, element, true)) return body;
    }

    //maybe inside class initalizer ?
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    for (int i = 0; i < initializers.length; i++) {
      PsiClassInitializer initializer = initializers[i];
      final PsiCodeBlock body = initializer.getBody();
      if (body == null) continue;
      if (PsiTreeUtil.isAncestor(body, element, true)) return body;
    }
    return null;
  }

  private void deferLocalVariable(PsiLocalVariable variable) throws IncorrectOperationException {
    PsiElement outerCodeBlock = PsiUtil.getVariableCodeBlock(variable, null);
    deferVariable(outerCodeBlock, variable, variable.getParent());
  }

  private void deferVariable(PsiElement outerCodeBlock, PsiVariable variable, PsiElement tempDeclarationAnchor) throws IncorrectOperationException {
    if (outerCodeBlock == null) return;
    List<PsiReferenceExpression> outerReferences = new ArrayList<PsiReferenceExpression>();
    collectReferences(outerCodeBlock, variable, outerReferences);

    final PsiElementFactory factory = variable.getManager().getElementFactory();
    final Project project = variable.getProject();
    final String tempName = suggestNewName(project, variable);
    final PsiDeclarationStatement tempVariableDeclaration = factory.createVariableDeclarationStatement(tempName, variable.getType(), null);

    final ControlFlow controlFlow;
    try {
      controlFlow = ControlFlowFactory.getControlFlow(outerCodeBlock, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
    }
    catch (ControlFlowAnalyzer.AnalysisCanceledException e) {
      return;
    }
    int minOffset = 0;
    boolean writeReferenceOccurred = false;
    PsiReferenceExpression writeReference = null;
    for (int i = outerReferences.size()-1; i>=0; i--) {
      PsiReferenceExpression reference = outerReferences.get(i);
      if (!writeReferenceOccurred && !PsiUtil.isAccessedForWriting(reference)) {
        // trailing read references need not be converted to temp var references
        outerReferences.remove(i);
        continue;
      }
      writeReferenceOccurred = true;
      writeReference = reference;
      PsiElement element = PsiUtil.getEnclosingStatement(reference);
      final int endOffset = element == null ? -1 : controlFlow.getEndOffset(element);
      minOffset = Math.max(minOffset, endOffset);
    }
    LOG.assertTrue(writeReference != null);
    final PsiStatement finalAssignment = factory.createStatementFromText(writeReference.getText()+" = "+tempName+";", outerCodeBlock);
    if (!insertToDefinitelyReachedPlace(outerCodeBlock, finalAssignment, controlFlow, minOffset, outerReferences)) return;

    outerCodeBlock.addAfter(tempVariableDeclaration, tempDeclarationAnchor);

    replaceReferences(outerReferences, factory.createExpressionFromText(tempName, outerCodeBlock));
  }


  private boolean insertToDefinitelyReachedPlace(PsiElement codeBlock, PsiStatement finalAssignment, ControlFlow controlFlow, int minOffset, List references) throws IncorrectOperationException {
    int offset = ControlFlowUtil.getMinDefinitelyReachedOffset(controlFlow, minOffset, references);
    if (offset == controlFlow.getSize()) {
      codeBlock.add(finalAssignment);
      return true;
    }
    PsiElement element = null; //controlFlow.getEndOffset(codeBlock) == offset ? getEnclosingStatement(controlFlow.getElement(offset)) : null;
    while (offset < controlFlow.getSize()) {
      element = controlFlow.getElement(offset);
      if (element != null) element = PsiUtil.getEnclosingStatement(element);
      final int startOffset = controlFlow.getStartOffset(element);
      if (startOffset != -1 && startOffset >= minOffset && element instanceof PsiStatement) break;
      offset++;
    }
    if (!(offset < controlFlow.getSize())) return false;
    // inside loop
    if (ControlFlowUtil.isInstructionReachable(controlFlow, offset, offset)) return false;
    codeBlock.addBefore(finalAssignment, element);
    return true;
  }

  private static void replaceReferences(List references, PsiElement newExpression) throws IncorrectOperationException {
    for (int i = 0; i < references.size(); i++) {
      PsiElement reference = (PsiElement) references.get(i);
      reference.replace(newExpression);
    }


  }

  private static void collectReferences(PsiElement context, final PsiVariable variable, final List<PsiReferenceExpression> references) {
    context.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.resolve() == variable) references.add(expression);
        super.visitReferenceExpression(expression);
      }
    });
  }

  private static String suggestNewName(Project project, PsiVariable variable) {
    // new name should not conflict with another variable at the variable declaration level and usage level
    String name = variable.getName();
    // trim last digit to suggest variable names like i1,i2, i3...
    if (name.length() > 1 && Character.isDigit(name.charAt(name.length()-1))) {
      name = name.substring(0,name.length()-1);
    }
    return CodeStyleManager.getInstance(project).suggestUniqueVariableName(name, variable, true);
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return
        variable != null
        && variable.isValid()
        && !(variable instanceof PsiParameter)
        && !(variable instanceof ImplicitVariable)
        && expression != null
        && expression.isValid()
        && variable.getManager().isInProject(variable)
        ;
  }

  public boolean startInWriteAction() {
    return true;
  }
}
