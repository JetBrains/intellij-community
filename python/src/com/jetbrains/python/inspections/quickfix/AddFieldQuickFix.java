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
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Available on self.my_something when my_something is unresolved.
 * User: dcheryasov
 */
public class AddFieldQuickFix implements LocalQuickFix {

  private final String myInitializer;
  private final String myClassName;
  private String myIdentifier;
  private boolean replaceInitializer = false;

  public AddFieldQuickFix(@NotNull final String identifier, @NotNull final String initializer, final String className, boolean replace) {
    myIdentifier = identifier;
    myInitializer = initializer;
    myClassName = className;
    replaceInitializer = replace;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", myIdentifier, myClassName);
  }

  @NotNull
  public String getFamilyName() {
    return "Add field to class";
  }

  @NotNull
  public static PsiElement appendToMethod(PyFunction init, Function<String, PyStatement> callback) {
    // add this field as the last stmt of the constructor
    final PyStatementList statementList = init.getStatementList();
    // name of 'self' may be different for fancier styles
    String selfName = PyNames.CANONICAL_SELF;
    final PyParameter[] params = init.getParameterList().getParameters();
    if (params.length > 0) {
      selfName = params[0].getName();
    }
    final PyStatement newStmt = callback.fun(selfName);
    final PsiElement result = PyUtil.addElementToStatementList(newStmt, statementList, true);
    PyPsiUtils.removeRedundantPass(statementList);
    return result;
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    // expect the descriptor to point to the unresolved identifier.
    final PsiElement element = descriptor.getPsiElement();
    final PyClassType type = getClassType(element);
    if (type == null) return;
    final PyClass cls = type.getPyClass();
    if (!FileModificationService.getInstance().preparePsiElementForWrite(cls)) return;

    WriteAction.run(() -> {
      PsiElement initStatement;
      if (!type.isDefinition()) {
        initStatement = addFieldToInit(project, cls, myIdentifier, new CreateFieldCallback(project, myIdentifier, myInitializer));
      }
      else {
        PyStatement field = PyElementGenerator.getInstance(project)
          .createFromText(LanguageLevel.getDefault(), PyStatement.class, myIdentifier + " = " + myInitializer);
        initStatement = PyUtil.addElementToStatementList(field, cls.getStatementList(), true);
      }
      if (initStatement != null) {
        showTemplateBuilder(initStatement, cls.getContainingFile());
        return;
      }
      // somehow we failed. tell about this
      PyUtil.showBalloon(project, PyBundle.message("QFIX.failed.to.add.field"), MessageType.ERROR);
    });
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static PyClassType getClassType(@NotNull final PsiElement element) {
    if (element instanceof PyQualifiedExpression) {
      final PyExpression qualifier = ((PyQualifiedExpression)element).getQualifier();
      if (qualifier == null) return null;
      final PyType type = TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile()).getType(qualifier);
      return type instanceof PyClassType ? (PyClassType)type : null;
    }
    final PyClass aClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
    return aClass != null ? new PyClassTypeImpl(aClass, false) : null;
  }

  private void showTemplateBuilder(PsiElement initStatement, @NotNull final PsiFile file) {
    initStatement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(initStatement);
    if (initStatement instanceof PyAssignmentStatement) {
      final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(initStatement);
      final PyExpression assignedValue = ((PyAssignmentStatement)initStatement).getAssignedValue();
      final PyExpression leftExpression = ((PyAssignmentStatement)initStatement).getLeftHandSideExpression();
      if (assignedValue != null && leftExpression != null) {
        if (replaceInitializer)
          builder.replaceElement(assignedValue, myInitializer);
        else
          builder.replaceElement(leftExpression.getLastChild(), myIdentifier);
        final VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) return;
        final Editor editor = FileEditorManager.getInstance(file.getProject()).openTextEditor(
                  new OpenFileDescriptor(file.getProject(), virtualFile), true);
        if (editor == null) return;
        builder.run(editor, false);
      }
    }
  }

  @Nullable
  public static PsiElement addFieldToInit(Project project, PyClass cls, String itemName, Function<String, PyStatement> callback) {
    if (cls != null && itemName != null) {
      PyFunction init = cls.findMethodByName(PyNames.INIT, false, null);
      if (init != null) {
        return appendToMethod(init, callback);
      }
      else { // no init! boldly copy ancestor's.
        for (PyClass ancestor : cls.getAncestorClasses(null)) {
          init = ancestor.findMethodByName(PyNames.INIT, false, null);
          if (init != null) break;
        }
        PyFunction newInit = createInitMethod(project, cls, init);
        appendToMethod(newInit, callback);

        PsiElement addAnchor = null;
        PyFunction[] meths = cls.getMethods();
        if (meths.length > 0) addAnchor = meths[0].getPrevSibling();
        PyStatementList clsContent = cls.getStatementList();
        newInit = (PyFunction) clsContent.addAfter(newInit, addAnchor);

        PyUtil.showBalloon(project, PyBundle.message("QFIX.added.constructor.$0.for.field.$1", cls.getName(), itemName), MessageType.INFO);
        final PyStatementList statementList = newInit.getStatementList();
        final PyStatement[] statements = statementList.getStatements();
        return statements.length != 0 ? statements[0] : null;
      }
    }
    return null;
  }

  @NotNull
  private static PyFunction createInitMethod(Project project, PyClass cls, @Nullable PyFunction ancestorInit) {
    // found it; copy its param list and make a call to it.
    String paramList = ancestorInit != null ? ancestorInit.getParameterList().getText() : "(self)";

    String functionText = "def " + PyNames.INIT + paramList + ":\n";
    if (ancestorInit == null) functionText += "    pass";
    else {
      final PyClass ancestorClass = ancestorInit.getContainingClass();
      if (ancestorClass != null && !PyUtil.isObjectClass(ancestorClass)) {
        StringBuilder sb = new StringBuilder();
        PyParameter[] params = ancestorInit.getParameterList().getParameters();

        boolean seen = false;
        if (cls.isNewStyleClass(null)) {
          // form the super() call
          sb.append("super(");
          if (!LanguageLevel.forElement(cls).isPy3K()) {
            sb.append(cls.getName());

            // NOTE: assume that we have at least the first param
            String self_name = params[0].getName();
            sb.append(", ").append(self_name);
          }
          sb.append(").").append(PyNames.INIT).append("(");
        }
        else {
          sb.append(ancestorClass.getName());
          sb.append(".__init__(self");
          seen = true;
        }
        for (int i = 1; i < params.length; i += 1) {
          if (seen) sb.append(", ");
          else seen = true;
          sb.append(params[i].getText());
        }
        sb.append(")");
        functionText += "    " + sb.toString();
      }
      else {
        functionText += "    pass";
      }
    }
    return PyElementGenerator.getInstance(project).createFromText(
      LanguageLevel.getDefault(), PyFunction.class, functionText,
      new int[]{0}
    );
  }

  private static class CreateFieldCallback implements Function<String, PyStatement> {
    private Project myProject;
    private String myItemName;
    private String myInitializer;

    private CreateFieldCallback(Project project, String itemName, String initializer) {
      myProject = project;
      myItemName = itemName;
      myInitializer = initializer;
    }

    public PyStatement fun(String selfName) {
      return PyElementGenerator.getInstance(myProject).createFromText(LanguageLevel.getDefault(), PyStatement.class, selfName + "." + myItemName + " = " + myInitializer);
    }
  }
}
