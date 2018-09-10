// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * Adds a missing top-level function to a module.
 * <br/>
 * User: dcheryasov
 * @see AddMethodQuickFix AddMethodQuickFix
 */
public class AddFunctionQuickFix  implements LocalQuickFix {

  private final String myIdentifier;
  private final String myModuleName;

  public AddFunctionQuickFix(@NotNull String identifier, String moduleName) {
    myIdentifier = identifier;
    myModuleName = moduleName;
  }

  @Override
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.NAME.add.function.$0.to.module.$1", myIdentifier, myModuleName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return "Create function in module";
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    try {
      final PsiElement problemElement = descriptor.getPsiElement();
      if (!(problemElement instanceof PyQualifiedExpression)) return;
      final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
      if (qualifier == null) return;
      final PyType type = TypeEvalContext.userInitiated(problemElement.getProject(), problemElement.getContainingFile()).getType(qualifier);
      if (!(type instanceof PyModuleType)) return;
      final PyFile file = ((PyModuleType)type).getModule();
      sure(file);
      sure(FileModificationService.getInstance().preparePsiElementForWrite(file));
      // try to at least match parameter count
      // TODO: get parameter style from code style
      PyFunctionBuilder builder = new PyFunctionBuilder(myIdentifier, problemElement);
      PsiElement problemParent = problemElement.getParent();
      if (problemParent instanceof PyCallExpression) {
        PyArgumentList arglist = ((PyCallExpression)problemParent).getArgumentList();
        if (arglist == null) return;
        final PyExpression[] args = arglist.getArguments();
        for (PyExpression arg : args) {
          if (arg instanceof PyKeywordArgument) { // foo(bar) -> def foo(bar_1)
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
      }
      else if (problemParent != null) {
        for (PyInspectionExtension extension : Extensions.getExtensions(PyInspectionExtension.EP_NAME)) {
          List<String> params = extension.getFunctionParametersFromUsage(problemElement);
          if (params != null) {
            for (String param : params) {
              builder.parameter(param);
            }
            break;
          }
        }
      }
      // else: no arglist, use empty args

      WriteAction.run(() -> {
        PyFunction function = builder.buildFunction();

        // add to the bottom
        function = (PyFunction) file.add(function);
        showTemplateBuilder(function, file);
      });
    }
    catch (IncorrectOperationException ignored) {
      // we failed. tell about this
      PyUtil.showBalloon(project, PyBundle.message("QFIX.failed.to.add.function"), MessageType.ERROR);
    }
  }

  private static void showTemplateBuilder(PyFunction method, @NotNull final PsiFile file) {
    method = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(method);

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

    // TODO: detect expected return type from call site context: PY-1863
    builder.replaceElement(method.getStatementList(), "return None");
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return;
    final Editor editor = FileEditorManager.getInstance(file.getProject()).openTextEditor(
      new OpenFileDescriptor(file.getProject(), virtualFile), true);
    if (editor == null) return;
    builder.run(editor, false);
  }
}
