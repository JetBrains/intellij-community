// Copyright 2000-2025 JetBrains s.r.o. and contributors.
package com.jetbrains.python.psi.impl.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class PyKeywordReference extends PsiReferenceBase.Poly<PyElement> {

  private final PyResolveContext myContext;

  public PyKeywordReference(@NotNull PyElement owner, @NotNull PyResolveContext context, @NotNull TextRange rangeInOwner) {
    super(owner, rangeInOwner, false);
    myContext = context;
  }

  @Override
  public boolean isSoft() {
    return true;
  }

  @Override
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    final PyElement element = getElement();
    if (element instanceof PyForPart forPart) {
      final PyExpression source = forPart.getSource();
      if (source == null) return ResolveResult.EMPTY_ARRAY;

      final TypeEvalContext typeEvalContext = myContext.getTypeEvalContext();
      final PyType type = typeEvalContext.getType(source);
      if (type == null) return ResolveResult.EMPTY_ARRAY;

      final List<RatedResolveResult> results = new ArrayList<>();

      boolean isAsync = false;
      PyForStatement forStatement = PsiTreeUtil.getParentOfType(forPart, PyForStatement.class);
      if (forStatement != null) {
        isAsync = forStatement.isAsync();
      }

      final String iterName = isAsync ? PyNames.AITER : PyNames.ITER;

      var members = type.resolveMember(iterName, source, AccessDirection.READ, myContext);
      if (members != null) results.addAll(members);

      if (results.isEmpty()) {
        members = type.resolveMember(PyNames.GETITEM, source, AccessDirection.READ, myContext);
        if (members != null) results.addAll(members);
      }

      return RatedResolveResult.sorted(results).toArray(ResolveResult.EMPTY_ARRAY);
    }
    return ResolveResult.EMPTY_ARRAY;
  }
}
