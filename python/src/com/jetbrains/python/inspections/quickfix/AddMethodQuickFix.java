/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * Adds a method foo to class X if X.foo() is unresolved.
 * User: dcheryasov
 */
public class AddMethodQuickFix implements LocalQuickFix {

  private final String myClassName;
  private final boolean myReplaceUsage;
  private String myIdentifier;

  public AddMethodQuickFix(String identifier, String className,
                           boolean replaceUsage) {
    myIdentifier = identifier;
    myClassName = className;
    myReplaceUsage = replaceUsage;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", myIdentifier, myClassName);
  }

  @NotNull
  public String getFamilyName() {
    return "Add method to class";
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    try {
      // there can be no name clash, else the name would have resolved, and it hasn't.
      final PsiElement problemElement = descriptor.getPsiElement();
      final PyClassType type = getClassType(problemElement);
      if (type == null) return;
      final PyClass cls = type.getPyClass();
      boolean callByClass = type.isDefinition();
      PyStatementList clsStmtList = cls.getStatementList();
      sure(FileModificationService.getInstance().preparePsiElementForWrite(clsStmtList));
      // try to at least match parameter count
      // TODO: get parameter style from code style
      PyFunctionBuilder builder = new PyFunctionBuilder(myIdentifier, cls);
      PsiElement pe = problemElement.getParent();
      String decoratorName = null; // set to non-null to add a decorator
      PyExpression[] args = PyExpression.EMPTY_ARRAY;
      if (pe instanceof PyCallExpression) {
        PyArgumentList arglist = ((PyCallExpression)pe).getArgumentList();
        if (arglist == null) return;
        args = arglist.getArguments();
      }
      boolean madeInstance = false;
      if (callByClass) {
        if (args.length > 0) {
          final TypeEvalContext context = TypeEvalContext.userInitiated(cls.getProject(), cls.getContainingFile());
          final PyType firstArgType = context.getType(args[0]);
          if (firstArgType instanceof PyClassType && ((PyClassType)firstArgType).getPyClass().isSubclass(cls, context)) {
            // class, first arg ok: instance method
            builder.parameter("self"); // NOTE: might use a name other than 'self', according to code style.
            madeInstance = true;
          }
        }
        if (!madeInstance) { // class, first arg absent or of different type: classmethod
          builder.parameter("cls"); // NOTE: might use a name other than 'cls', according to code style.
          decoratorName = PyNames.CLASSMETHOD;
        }
      }
      else { // instance method
        builder.parameter("self"); // NOTE: might use a name other than 'self', according to code style.
      }
      boolean skipFirst = callByClass && madeInstance; // ClassFoo.meth(foo_instance)
      for (PyExpression arg : args) {
        if (skipFirst) {
          skipFirst = false;
          continue;
        }
        if (arg instanceof PyKeywordArgument) { // foo(bar) -> def foo(self, bar_1)
          builder.parameter(((PyKeywordArgument)arg).getKeyword());
        }
        else if (arg instanceof PyReferenceExpression) {
          PyReferenceExpression refex = (PyReferenceExpression)arg;
          builder.parameter(refex.getReferencedName());
        }
        else { // use a boring name
          builder.parameter("param");
        }
      }
      PyFunction method = builder.buildFunction(project, LanguageLevel.getDefault());
      if (decoratorName != null) {
        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        PyDecoratorList decoratorList = generator
          .createFromText(LanguageLevel.getDefault(), PyDecoratorList.class, "@" + decoratorName + "\ndef foo(): pass", new int[]{0, 0});
        method.addBefore(decoratorList, method.getFirstChild()); // in the very beginning
      }

      method = (PyFunction)PyUtil.addElementToStatementList(method, clsStmtList, PyNames.INIT.equals(method.getName()));
      if (myReplaceUsage) {
        showTemplateBuilder(method);
      }
    }
    catch (IncorrectOperationException ignored) {
      // we failed. tell about this
      PyUtil.showBalloon(project, PyBundle.message("QFIX.failed.to.add.method"), MessageType.ERROR);
    }
  }

  private static PyClassType getClassType(@NotNull final PsiElement problemElement) {
    if ((problemElement instanceof PyQualifiedExpression)) {
      final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
      if (qualifier == null) return null;
      final PyType type = TypeEvalContext.userInitiated(problemElement.getProject(), problemElement.getContainingFile()).getType(qualifier);
      return type instanceof PyClassType ? (PyClassType)type : null;
    }
    final PyClass pyClass = PsiTreeUtil.getParentOfType(problemElement, PyClass.class);
    return pyClass != null ? new PyClassTypeImpl(pyClass, false) : null;
  }

  private static void showTemplateBuilder(@NotNull PyFunction method) {
    method = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(method);
    final PsiFile file = method.getContainingFile();
    if (file == null) return;
    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(method);
    ParamHelper.walkDownParamArray(
      method.getParameterList().getParameters(),
      new ParamHelper.ParamVisitor() {
        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          builder.replaceElement(param, param.getName());
        }
      }
    );

    final PyStatementList statementList = method.getStatementList();
    builder.replaceElement(statementList, PyNames.PASS);

    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return;
    final Editor editor = FileEditorManager.getInstance(file.getProject()).openTextEditor(
      new OpenFileDescriptor(file.getProject(), virtualFile), true);
    if (editor == null) return;
    builder.run(editor, false);
  }
}
