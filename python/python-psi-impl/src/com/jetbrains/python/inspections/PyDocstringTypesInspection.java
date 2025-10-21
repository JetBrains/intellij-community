// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.documentation.docstrings.PlainDocString;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.toolbox.Substring;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.psi.PyUtil.as;

public final class PyDocstringTypesInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction function) {
      final String name = function.getName();
      if (name != null && !name.startsWith("_")) checkDocString(function);
    }

    private void checkDocString(@NotNull PyFunction function) {
      final PyStringLiteralExpression docStringExpression = function.getDocStringExpression();
      if (docStringExpression != null) {
        PySignatureCacheManager manager = PySignatureCacheManager.getInstance(function.getProject());
        if (manager != null) {
          PySignature signature = manager.findSignature(function);
          if (signature != null) {
            checkParameters(function, docStringExpression, signature);
          }
        }
      }
    }

    private void checkParameters(PyFunction function, PyStringLiteralExpression node, PySignature signature) {
      final StructuredDocString docString = DocStringUtil.parseDocString(node);
      if (docString instanceof PlainDocString) {
        return;
      }

      for (String param : docString.getParameters()) {
        @NlsSafe Substring type = docString.getParamTypeSubstring(param);
        if (type != null) {
          String dynamicType = signature.getArgTypeQualifiedName(param);
          if (dynamicType != null) {
            @NlsSafe String dynamicTypeShortName = getShortestImportableName(function, dynamicType);
            if (dynamicTypeShortName != null && !match(function, dynamicType, type.getValue())) {
              registerProblem(node, PyPsiBundle.message("INSP.docstring.types.dynamically.inferred.type.does.not.match.specified.type",
                                                        dynamicTypeShortName, type),
                              ProblemHighlightType.WEAK_WARNING, null, type.getTextRange(),
                              new ChangeTypeQuickFix(param, type, dynamicTypeShortName)
              );
            }
          }
        }
      }
    }

    private @Nullable String getShortestImportableName(@Nullable PsiElement anchor, @NotNull String type) {
      final PyType pyType = PyTypeParser.getTypeByName(anchor, type, myTypeEvalContext);
      if (pyType instanceof PyClassType) {
        return ((PyClassType)pyType).getPyClass().getQualifiedName();
      }

      if (pyType != null) {
        return getPrintableName(pyType);
      }
      else {
        return type;
      }
    }

    private static @Nullable String getPrintableName(@Nullable PyType type) {
      if (type instanceof PyUnionType unionType) {
        return StreamEx
          .of(unionType.getMembers())
          .map(Visitor::getPrintableName)
          .joining(" or ");
      }
      else if (type != null) {
        return type.getName();
      }
      else {
        return PyNames.UNKNOWN_TYPE;
      }
    }

    private boolean match(PsiElement anchor, String dynamicTypeName, String specifiedTypeName) {
      final PyType dynamicType = PyTypeParser.getTypeByName(anchor, dynamicTypeName, myTypeEvalContext);
      final PyType specifiedType = PyTypeParser.getTypeByName(anchor, specifiedTypeName, myTypeEvalContext);
      return PyTypeChecker.match(specifiedType, dynamicType, myTypeEvalContext);
    }
  }


  private static final class ChangeTypeQuickFix extends PsiUpdateModCommandQuickFix {
    private final String myParamName;
    private final Substring myTypeSubstring;
    private final String myNewType;

    private ChangeTypeQuickFix(@NotNull String name, @NotNull Substring substring, @NotNull String type) {
      myParamName = name;
      myTypeSubstring = substring;
      myNewType = type;
    }

    @Override
    public @NotNull String getName() {
      return PyPsiBundle.message("INSP.docstring.types.change.type", myParamName, myTypeSubstring.getValue(), myNewType);
    }

    @Override
    public @NotNull String getFamilyName() {
      return PyPsiBundle.message("INSP.docstring.types.fix.docstring");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      String newValue = myTypeSubstring.getTextRange().replace(myTypeSubstring.getSuperString(), myNewType);

      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

      final PyStringLiteralExpression stringLiteralExpression = as(element, PyStringLiteralExpression.class);
      if (stringLiteralExpression != null) {
        stringLiteralExpression.replace(elementGenerator.createDocstring(newValue).getExpression());
      }
    }
  }
}

