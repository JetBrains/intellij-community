// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.references;

import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class PyAugOperatorReference extends PyReferenceImpl {

  public PyAugOperatorReference(PyAugAssignmentStatement element, @NotNull PyResolveContext context) {
    super(element, context);
  }

  @NotNull
  @Override
  public PyAugAssignmentStatement getElement() {
    return (PyAugAssignmentStatement)super.getElement();
  }

  @Override
  public String toString() {
    return "PyAugOperatorReference(" + myElement + "," + myContext + ")";
  }

  @NotNull
  @Override
  protected List<RatedResolveResult> resolveInner() {
    final PyAugAssignmentStatement element = getElement();

    final PyExpression target = element.getTarget();
    final String referencedName = element.getReferencedName();
    if (referencedName == null) return Collections.emptyList();

    final List<? extends RatedResolveResult> augMembers = resolveMember(target, referencedName);
    if (!augMembers.isEmpty()) return new ArrayList<>(augMembers);

    final PyExpression lhs = getElement().getTarget();
    final PyExpression rhs = getElement().getValue();

    final String leftName = referencedName.replace("__i", "__");
    final String rightName = PyNames.leftToRightOperatorName(leftName);

    return ContainerUtil.concat(resolveMember(lhs, leftName), resolveMember(rhs, rightName));
  }

  @NotNull
  private List<? extends RatedResolveResult> resolveMember(@Nullable PyExpression object, @NotNull String name) {
    return Optional
      .ofNullable(object)
      .map(obj -> myContext.getTypeEvalContext().getType(obj))
      .map(targetType -> targetType.resolveMember(name, object, AccessDirection.READ, myContext))
      .orElse(Collections.emptyList());
  }
}
