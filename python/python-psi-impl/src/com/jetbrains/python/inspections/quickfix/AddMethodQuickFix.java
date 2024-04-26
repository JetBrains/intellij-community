// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * Adds a method foo to class X if X.foo() is unresolved.
 */
public class AddMethodQuickFix implements LocalQuickFix {

  private final String myClassName;
  private final boolean myReplaceUsage;
  private final String myIdentifier;

  public AddMethodQuickFix(String identifier, String className,
                           boolean replaceUsage) {
    myIdentifier = identifier;
    myClassName = className;
    myReplaceUsage = replaceUsage;
  }

  @Override
  @NotNull
  public String getName() {
    return PyPsiBundle.message("QFIX.add.method.to.class", myIdentifier, myClassName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.add.method.to.class");
  }

  @Override
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
      if (pe instanceof PyCallExpression callExpression) {
        PyArgumentList arglist = callExpression.getArgumentList();
        if (arglist == null) return;
        args = arglist.getArguments();
        if (callExpression.getParent() instanceof PyPrefixExpression prefixExpression &&
            prefixExpression.getOperator() == PyTokenTypes.AWAIT_KEYWORD) {
          builder.makeAsync();
        }
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
        else if (arg instanceof PyReferenceExpression refex) {
          builder.parameter(refex.getReferencedName());
        }
        else { // use a boring name
          builder.parameter("param");
        }
      }
      PyFunction method = builder.buildFunction();
      if (decoratorName != null) {
        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        PyDecoratorList decoratorList = generator
          .createFromText(LanguageLevel.getDefault(), PyDecoratorList.class, "@" + decoratorName + "\ndef foo(): pass", new int[]{0, 0});
        method.addBefore(decoratorList, method.getFirstChild()); // in the very beginning
      }

      method = (PyFunction)PyPsiRefactoringUtil.addElementToStatementList(method, clsStmtList, PyNames.INIT.equals(method.getName()));
      if (myReplaceUsage) {
        showTemplateBuilder(method);
      }
    }
    catch (IncorrectOperationException ignored) {
      // we failed. tell about this
      PythonUiService.getInstance().showBalloonError(project, PyPsiBundle.message("QFIX.failed.to.add.method"));
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
        @Override
        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          builder.replaceElement(param, param.getName());
        }
      }
    );

    final PyStatementList statementList = method.getStatementList();
    builder.replaceElement(statementList, PyNames.PASS);
    PythonTemplateRunner.runTemplate(file, builder);
  }
}
