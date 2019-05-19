// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: ktisha
 * <p/>
 * Helps to specify type
 */
public class SpecifyTypeInDocstringIntention extends TypeIntention {
  private String myText = PyBundle.message("INTN.specify.type");

  @Override
  @NotNull
  public String getText() {
    return myText;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.specify.type");
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

    if (!DocStringUtil.ensureNotPlainDocstringFormat(pyFunction)) return;

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
      docstringGenerator.startTemplate();
    });
  }

  @Override
  protected void updateText(boolean isReturn) {
    myText = PyBundle.message(isReturn ? "INTN.specify.return.type" : "INTN.specify.type");
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
}
