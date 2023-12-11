// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.google.common.base.Preconditions;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: ktisha
 * <p/>
 * Helps to specify type
 */
public final class SpecifyTypeInDocstringIntention extends TypeIntention {
  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.specify.type.in.docstring");
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PyNamedParameter parameter = findOnlySuitableParameter(editor, file);
    if (parameter != null) {
      final PyFunction parentFunction = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
      if (parentFunction != null) {
        generateDocstring(parameter, parentFunction);
      }
      return;
    }

    final PyFunction function = findOnlySuitableFunction(editor, file);
    if (function != null) {
      generateDocstring(null, function);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static void generateDocstring(@Nullable PyNamedParameter param, @NotNull PyFunction pyFunction) {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(pyFunction)) return;

    if (!PyGenerateDocstringIntention.ensureNotPlainDocstringFormat(pyFunction)) return;

    final PyDocstringGenerator docstringGenerator = PyDocstringGenerator.forDocStringOwner(pyFunction);
    String type = PyNames.OBJECT;
    if (param != null) {
      final String paramName = StringUtil.notNullize(param.getName());
      final PySignature signature = PySignatureCacheManager.getInstance(pyFunction.getProject()).findSignature(pyFunction);
      if (signature != null) {
        type = ObjectUtils.chooseNotNull(signature.getArgTypeQualifiedName(paramName), type);
      }
      docstringGenerator.withParamTypedByName(param, type);
    }
    else {
      final PySignature signature = PySignatureCacheManager.getInstance(pyFunction.getProject()).findSignature(pyFunction);
      if (signature != null) {
        type = ObjectUtils.chooseNotNull(signature.getReturnTypeQualifiedName(), type);
      }
      docstringGenerator.withReturnValue(type);
    }

    WriteAction.run(() -> {
      docstringGenerator.addFirstEmptyLine().buildAndInsert();
      startTemplate(docstringGenerator);
    });
  }

  @Override
  protected void updateText(boolean isReturn) {
    setText(PyPsiBundle.message(isReturn ? "INTN.specify.return.type.in.docstring" : "INTN.specify.type.in.docstring"));
  }

  @Override
  protected boolean isParamTypeDefined(@NotNull PyNamedParameter parameter) {
    final PyFunction pyFunction = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
    if (pyFunction != null) {
      final StructuredDocString structuredDocString = pyFunction.getStructuredDocString();
      if (structuredDocString == null) {
        return false;
      }
      final Substring typeSub = structuredDocString.getParamTypeSubstring(StringUtil.notNullize(parameter.getName()));
      return typeSub != null && !typeSub.isEmpty();
    }
    return false;
  }

  @Override
  protected boolean isReturnTypeDefined(@NotNull PyFunction function) {
    final StructuredDocString structuredDocString = function.getStructuredDocString();
    return structuredDocString != null && structuredDocString.getReturnType() != null;
  }

  public static void startTemplate(PyDocstringGenerator generator) {
    Preconditions.checkNotNull(generator.getDocStringOwner(), "For this action docstring owner must be supplied");
    final PyStringLiteralExpression docStringExpression = generator.getDocStringExpression();
    assert docStringExpression != null;

    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(docStringExpression);

    if (generator.getAddedParams().size() > 1) {
      throw new IllegalArgumentException("TemplateBuilder can be created only for one parameter");
    }

    final PyDocstringGenerator.DocstringParam paramToEdit = generator.getParamToEdit();
    final DocStringFormat format = generator.getDocStringFormat();
    if (format == DocStringFormat.PLAIN) {
      return;
    }
    final StructuredDocString parsed = DocStringUtil.parseDocString(format, docStringExpression);
    final Substring substring;
    if (paramToEdit.isReturnValue()) {
      substring = parsed.getReturnTypeSubstring();
    }
    else {
      final String paramName = paramToEdit.getName();
      substring = parsed.getParamTypeSubstring(paramName);
    }
    if (substring == null) {
      return;
    }
    builder.replaceRange(substring.getTextRange(), PyDocstringGenerator.getDefaultType(generator.getParamToEdit()));

    final VirtualFile virtualFile = generator.getDocStringOwner().getContainingFile().getVirtualFile();
    if (virtualFile == null) return;
    final Editor targetEditor = PsiEditorUtil.findEditor(generator.getDocStringOwner());
    if (targetEditor != null) {
      PyUtil.updateDocumentUnblockedAndCommitted(generator.getDocStringOwner(), document -> {
        builder.run(targetEditor, true);
        return null;
      });

    }
  }
}
