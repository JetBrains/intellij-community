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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.debugger.PySignatureUtil;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.documentation.docstrings.PlainDocString;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.PyTypeParser;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class PyDocstringTypesInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.docstring.types");
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyFunction(PyFunction function) {
      final String name = function.getName();
      if (name != null && !name.startsWith("_")) checkDocString(function);
    }

    private void checkDocString(@NotNull PyFunction function) {
      final PyStringLiteralExpression docStringExpression = function.getDocStringExpression();
      if (docStringExpression != null) {
        PySignatureCacheManager manager = PySignatureCacheManager.getInstance(function.getProject());
        PySignature signature = manager.findSignature(function);
        if (signature != null) {
          checkParameters(function, docStringExpression, signature);
        }
      }
    }

    private void checkParameters(PyFunction function, PyStringLiteralExpression node, PySignature signature) {
      final StructuredDocString docString = DocStringUtil.parseDocString(node);
      if (docString instanceof PlainDocString) {
        return;
      }

      for (String param : docString.getParameters()) {
        Substring type = docString.getParamTypeSubstring(param);
        if (type != null) {
          String dynamicType = signature.getArgTypeQualifiedName(param);
          if (dynamicType != null) {
            String dynamicTypeShortName = PySignatureUtil.getShortestImportableName(function, dynamicType);
            if (!match(function, dynamicType, type.getValue())) {
              registerProblem(node, "Dynamically inferred type '" +
                                    dynamicTypeShortName +
                                    "' doesn't match specified type '" +
                                    type + "'",
                              ProblemHighlightType.WEAK_WARNING, null, type.getTextRange(),
                              new ChangeTypeQuickFix(param, type, dynamicTypeShortName, node)
              );
            }
          }
        }
      }
    }

    private boolean match(PsiElement anchor, String dynamicTypeName, String specifiedTypeName) {
      final PyType dynamicType = PyTypeParser.getTypeByName(anchor, dynamicTypeName);
      final PyType specifiedType = PyTypeParser.getTypeByName(anchor, specifiedTypeName);
      return PyTypeChecker.match(specifiedType, dynamicType, myTypeEvalContext);
    }
  }


  private static class ChangeTypeQuickFix implements LocalQuickFix {
    private final String myParamName;
    private final Substring myTypeSubstring;
    private final String myNewType;
    private final PyStringLiteralExpression myStringLiteralExpression;

    private ChangeTypeQuickFix(String name, Substring substring, String type, PyStringLiteralExpression expression) {
      myParamName = name;
      myTypeSubstring = substring;
      myNewType = type;
      myStringLiteralExpression = expression;
    }

    @NotNull
    @Override
    public String getName() {
      return "Change " + myParamName + " type from " + myTypeSubstring.getValue() + " to " + myNewType;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Fix docstring";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      String newValue = myTypeSubstring.getTextRange().replace(myTypeSubstring.getSuperString(), myNewType);

      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

      myStringLiteralExpression.replace(elementGenerator.createDocstring(newValue).getExpression());
    }
  }
}

