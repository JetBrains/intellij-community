/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.refactoring.introduce.field;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduce.inplace.InplaceVariableIntroducer;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.ThreeState;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.inspections.quickfix.AddFieldQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.IntroduceOperation;
import com.jetbrains.python.refactoring.introduce.variable.PyIntroduceVariableHandler;
import com.jetbrains.python.testing.PythonUnitTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class PyIntroduceFieldHandler extends IntroduceHandler {

  public PyIntroduceFieldHandler() {
    super(new IntroduceFieldValidator(), RefactoringBundle.message("introduce.field.title"));
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    final IntroduceOperation operation = new IntroduceOperation(project, editor, file, null);
    operation.addAvailableInitPlace(InitPlace.CONSTRUCTOR);
    if (isTestClass(file, editor)) {
      operation.addAvailableInitPlace(InitPlace.SET_UP);
    }
    performAction(operation);
  }

  private static boolean isTestClass(PsiFile file, Editor editor) {
    PsiElement element1 = null;
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      element1 = file.findElementAt(selectionModel.getSelectionStart());
    }
    else {
      final CaretModel caretModel = editor.getCaretModel();
      final Document document = editor.getDocument();
      int lineNumber = document.getLineNumber(caretModel.getOffset());
      if ((lineNumber >= 0) && (lineNumber < document.getLineCount())) {
        element1 = file.findElementAt(document.getLineStartOffset(lineNumber));
      }
    }
    if (element1 != null) {
      final PyClass clazz = PyUtil.getContainingClassOrSelf(element1);
      if (clazz != null && PythonUnitTestUtil.isTestClass(clazz, ThreeState.UNSURE, null)) return true;
    }
    return false;
  }

  @Override
  protected PsiElement replaceExpression(PsiElement expression, PyExpression newExpression, IntroduceOperation operation) {
    if (operation.getInitPlace() != InitPlace.SAME_METHOD) {
      return PyReplaceExpressionUtil.replaceExpression(expression, newExpression);
    }
    return super.replaceExpression(expression, newExpression, operation);
  }

  @Override
  protected boolean checkEnabled(IntroduceOperation operation) {
    if (PyUtil.getContainingClassOrSelf(operation.getElement()) == null) {
      CommonRefactoringUtil.showErrorHint(operation.getProject(), operation.getEditor(), "Cannot introduce field: not in class", myDialogTitle,
                                          getHelpId());
      return false;
    }
    if (dependsOnLocalScopeValues(operation.getElement())) {
      operation.removeAvailableInitPlace(InitPlace.CONSTRUCTOR);
      operation.removeAvailableInitPlace(InitPlace.SET_UP);
    }
    return true;
  }

  private static boolean dependsOnLocalScopeValues(PsiElement initializer) {
    ScopeOwner scope = PsiTreeUtil.getParentOfType(initializer, ScopeOwner.class);
    ResolvingVisitor visitor = new ResolvingVisitor(scope);
    initializer.accept(visitor);
    return visitor.hasLocalScopeDependencies;
    
  }
  
  private static class ResolvingVisitor extends PyRecursiveElementVisitor {
    private boolean hasLocalScopeDependencies = false;
    private final ScopeOwner myScope;

    public ResolvingVisitor(ScopeOwner scope) {
      myScope = scope;
    }

    @Override
    public void visitPyReferenceExpression(PyReferenceExpression node) {
      super.visitPyReferenceExpression(node);
      final PsiElement result = node.getReference().resolve();
      if (result != null && PsiTreeUtil.getParentOfType(result, ScopeOwner.class) == myScope) {
        if (result instanceof PyParameter && myScope instanceof PyFunction) {
          final PyFunction function = (PyFunction)myScope;
          final PyParameter[] parameters = function.getParameterList().getParameters();
          if (parameters.length > 0 && result == parameters[0]) {
            final PyFunction.Modifier modifier = function.getModifier();
            if (modifier != PyFunction.Modifier.STATICMETHOD) {
              // 'self' is not a local scope dependency
              return;
            }
          }
        }
        hasLocalScopeDependencies = true;
      }
    }
  }

  @Nullable
  @Override
  protected PsiElement addDeclaration(@NotNull PsiElement expression, @NotNull PsiElement declaration, @NotNull IntroduceOperation operation) {
    final PsiElement expr = expression instanceof PyClass ? expression : expression.getParent();    
    PsiElement anchor = PyUtil.getContainingClassOrSelf(expr);
    assert anchor instanceof PyClass;
    final PyClass clazz = (PyClass)anchor;
    final Project project = anchor.getProject();
    if (operation.getInitPlace() == InitPlace.CONSTRUCTOR && !inConstructor(expression)) {
      return AddFieldQuickFix.addFieldToInit(project, clazz, "", new AddFieldDeclaration(declaration));
    } else if (operation.getInitPlace() == InitPlace.SET_UP) {
      return addFieldToSetUp(clazz, new AddFieldDeclaration(declaration));
    }
    return PyIntroduceVariableHandler.doIntroduceVariable(expression, declaration, operation.getOccurrences(), operation.isReplaceAll());
  }

  private static boolean inConstructor(@NotNull PsiElement expression) {
    final PsiElement expr = expression instanceof PyClass ? expression : expression.getParent();
    PyClass clazz = PyUtil.getContainingClassOrSelf(expr);
    final ScopeOwner current = ScopeUtil.getScopeOwner(expression);
    if (clazz != null && current instanceof PyFunction) {
      PyFunction init = clazz.findMethodByName(PyNames.INIT, false, null);
      if (current == init) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static PsiElement addFieldToSetUp(PyClass clazz, final Function<String, PyStatement> callback) {
    final PyFunction init = clazz.findMethodByName(PythonUnitTestUtil.TESTCASE_SETUP_NAME, false, null);
    if (init != null) {
      return AddFieldQuickFix.appendToMethod(init, callback);
    }
    final PyFunctionBuilder builder = new PyFunctionBuilder(PythonUnitTestUtil.TESTCASE_SETUP_NAME, clazz);
    builder.parameter(PyNames.CANONICAL_SELF);
    PyFunction setUp = builder.buildFunction(clazz.getProject(), LanguageLevel.getDefault());
    final PyStatementList statements = clazz.getStatementList();
    final PsiElement anchor = statements.getFirstChild();
    setUp = (PyFunction)statements.addBefore(setUp, anchor);
    return AddFieldQuickFix.appendToMethod(setUp, callback);
  }

  @Override
  protected List<PsiElement> getOccurrences(PsiElement element, @NotNull PyExpression expression) {
    if (isAssignedLocalVariable(element)) {
      PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
      Collection<PsiReference> references = ReferencesSearch.search(element, new LocalSearchScope(function)).findAll();
      ArrayList<PsiElement> result = new ArrayList<>();
      for (PsiReference reference : references) {
        PsiElement refElement = reference.getElement();
        if (refElement != element) {
          result.add(refElement);
        }
      }
      return result;
    }
    return super.getOccurrences(element, expression);
  }

  @Override
  protected PyExpression createExpression(Project project, String name, PsiElement declaration) {
    final String text = declaration.getText();
    final String self_name = text.substring(0, text.indexOf('.'));
    return PyElementGenerator.getInstance(project).createExpressionFromText(self_name + "." + name);
  }

  @Override
  protected PyAssignmentStatement createDeclaration(Project project, String assignmentText, PsiElement anchor) {
    final PyFunction container = PsiTreeUtil.getParentOfType(anchor, PyFunction.class);
    String selfName = PyUtil.getFirstParameterName(container);
    final LanguageLevel langLevel = LanguageLevel.forElement(anchor);
    return PyElementGenerator.getInstance(project).createFromText(langLevel, PyAssignmentStatement.class, selfName + "." + assignmentText);
  }

  @Override
  protected void postRefactoring(PsiElement element) {
    if (isAssignedLocalVariable(element)) {
      element.getParent().delete();
    }
  }

  private static boolean isAssignedLocalVariable(PsiElement element) {
    if (element instanceof PyTargetExpression && element.getParent() instanceof PyAssignmentStatement &&
        PsiTreeUtil.getParentOfType(element, PyFunction.class) != null) {
      PyAssignmentStatement stmt = (PyAssignmentStatement) element.getParent();
      if (stmt.getTargets().length == 1) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected String getHelpId() {
    return "python.reference.introduceField";
  }

  @Override
  protected boolean checkIntroduceContext(PsiFile file, Editor editor, PsiElement element) {
    if (element != null && isInStaticMethod(element)) {
      CommonRefactoringUtil.showErrorHint(file.getProject(), editor, "Introduce Field refactoring cannot be used in static methods",
                                          RefactoringBundle.message("introduce.field.title"),
                                          "refactoring.extractMethod");
      return false;
    }
    return super.checkIntroduceContext(file, editor, element);
  }

  private static boolean isInStaticMethod(PsiElement element) {
    PyFunction containingMethod = PsiTreeUtil.getParentOfType(element, PyFunction.class, false, PyClass.class);
    if (containingMethod != null) {
      final PyFunction.Modifier modifier = containingMethod.getModifier();
      return modifier == PyFunction.Modifier.STATICMETHOD;
    }
    return false;
  }

  @Override
  protected boolean isValidIntroduceContext(PsiElement element) {
    return super.isValidIntroduceContext(element) &&
           PsiTreeUtil.getParentOfType(element, PyFunction.class, false, PyClass.class) != null &&
           PsiTreeUtil.getParentOfType(element, PyDecoratorList.class) == null &&
           !isInStaticMethod(element);
  }

  private static class AddFieldDeclaration implements Function<String, PyStatement> {
    private final PsiElement myDeclaration;

    private AddFieldDeclaration(PsiElement declaration) {
      myDeclaration = declaration;
    }

    public PyStatement fun(String self_name) {
      if (PyNames.CANONICAL_SELF.equals(self_name)) {
        return (PyStatement)myDeclaration;
      }
      final String text = myDeclaration.getText();
      final Project project = myDeclaration.getProject();
      return PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyStatement.class,
                                                                    text.replaceFirst(PyNames.CANONICAL_SELF + "\\.", self_name + "."));
    }
  }

  @Override
  protected void performInplaceIntroduce(IntroduceOperation operation) {
    final PsiElement statement = performRefactoring(operation);
    // put caret on identifier after "self."
    if (statement instanceof PyAssignmentStatement) {
        final List<PsiElement> occurrences = operation.getOccurrences();
        final PsiElement occurrence = findOccurrenceUnderCaret(occurrences, operation.getEditor());
        PyTargetExpression target = (PyTargetExpression) ((PyAssignmentStatement)statement).getTargets() [0];
        putCaretOnFieldName(operation.getEditor(), occurrence != null ? occurrence : target);
        final InplaceVariableIntroducer<PsiElement> introducer = new PyInplaceFieldIntroducer(target, operation, occurrences);
        introducer.performInplaceRefactoring(new LinkedHashSet<>(operation.getSuggestedNames()));
      }
    }

  private static void putCaretOnFieldName(Editor editor, PsiElement occurrence) {
    PyQualifiedExpression qExpr = PsiTreeUtil.getParentOfType(occurrence, PyQualifiedExpression.class, false);
    if (qExpr != null && !qExpr.isQualified()) {
      qExpr = PsiTreeUtil.getParentOfType(qExpr, PyQualifiedExpression.class);
    }
    if (qExpr != null) {
      final ASTNode nameElement = qExpr.getNameElement();
      if (nameElement != null) {
        final int offset = nameElement.getTextRange().getStartOffset();
        editor.getCaretModel().moveToOffset(offset);
      }
    }
  }

  private static class PyInplaceFieldIntroducer extends InplaceVariableIntroducer<PsiElement> {
    private final PyTargetExpression myTarget;
    private final IntroduceOperation myOperation;
    private final PyIntroduceFieldPanel myPanel;

    public PyInplaceFieldIntroducer(PyTargetExpression target,
                                    IntroduceOperation operation,
                                    List<PsiElement> occurrences) {
      super(target, operation.getEditor(), operation.getProject(), "Introduce Field",
            occurrences.toArray(new PsiElement[occurrences.size()]), null);
      myTarget = target;
      myOperation = operation;
      if (operation.getAvailableInitPlaces().size() > 1) {
        myPanel = new PyIntroduceFieldPanel(myProject, operation.getAvailableInitPlaces());
      }
      else {
        myPanel = null;
      }
    }

    @Override
    protected PsiElement checkLocalScope() {
      return myTarget.getContainingFile();
    }

    @Override
    protected JComponent getComponent() {
      return myPanel == null ? null : myPanel.getRootPanel();
    }

    @Override
    protected void moveOffsetAfter(boolean success) {
      if (success && (myPanel != null && myPanel.getInitPlace() != InitPlace.SAME_METHOD) || myOperation.getInplaceInitPlace() != InitPlace.SAME_METHOD) {
        WriteAction.run(() -> {
          final PyAssignmentStatement initializer = PsiTreeUtil.getParentOfType(myTarget, PyAssignmentStatement.class);
          assert initializer != null;
          final Function<String, PyStatement> callback = FunctionUtil.constant(initializer);
          final PyClass pyClass = PyUtil.getContainingClassOrSelf(initializer);
          InitPlace initPlace = myPanel != null ? myPanel.getInitPlace() : myOperation.getInplaceInitPlace();
          if (initPlace == InitPlace.CONSTRUCTOR) {
            AddFieldQuickFix.addFieldToInit(myProject, pyClass, "", callback);
          }
          else if (initPlace == InitPlace.SET_UP) {
            addFieldToSetUp(pyClass, callback);
          }
          if (myOperation.getOccurrences().size() > 0) {
            initializer.delete();
          }
          else {
            final PyExpression copy =
              PyElementGenerator.getInstance(myProject).createExpressionFromText(LanguageLevel.forElement(myTarget), myTarget.getText());
            initializer.replace(copy);
          }
          initializer.delete();
        });
      }
    }
  }

  @Override
  protected String getRefactoringId() {
    return "refactoring.python.introduce.field";
  }
}
