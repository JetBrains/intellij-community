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

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.inspections.quickfix.AddFieldQuickFix;
import com.jetbrains.python.inspections.quickfix.PyMoveAttributeToInitQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.testing.PythonUnitTestDetectorsKt;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User: ktisha
 *
 * Inspection to detect situations, where instance attribute is defined outside __init__ function.
 */
public final class PyAttributeOutsideInitInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }


  private static class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder,
            @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction node) {
      final PyClass containingClass = node.getContainingClass();
      if (containingClass == null) return;
      final String name = node.getName();
      if (name != null && name.startsWith("_")) return;
      if (!isApplicable(containingClass, myTypeEvalContext)) {
        return;
      }
      if (node.getModifier() != null) {
        return;
      }

      final Map<String, Property> localProperties = containingClass.getProperties();
      final Map<String, PyTargetExpression> declaredAttributes = new HashMap<>();
      final Set<String> inheritedProperties = new HashSet<>();

      StreamEx.of(containingClass.getClassAttributes())
              .filter(attribute -> !localProperties.containsKey(attribute.getName()))
              .forEach(attribute -> declaredAttributes.put(attribute.getName(), attribute));

      final PyFunction initMethod = containingClass.findMethodByName(PyNames.INIT, false, myTypeEvalContext);
      if (initMethod != null) {
        PyClassImpl.collectInstanceAttributes(initMethod, declaredAttributes);
      }
      for (PyClass superClass : containingClass.getAncestorClasses(myTypeEvalContext)) {
        final PyFunction superInit = superClass.findMethodByName(PyNames.INIT, false, myTypeEvalContext);
        if (superInit != null) {
          PyClassImpl.collectInstanceAttributes(superInit, declaredAttributes);
        }

        for (PyTargetExpression classAttr : superClass.getClassAttributes()) {
          declaredAttributes.put(classAttr.getName(), classAttr);
        }

        inheritedProperties.addAll(superClass.getProperties().keySet());
      }

      final Map<String, PyTargetExpression> attributes = new HashMap<>();
      PyClassImpl.collectInstanceAttributes(node, attributes);

      for (PyTargetExpression attribute : attributes.values()) {
        final String attributeName = attribute.getName();
        if (attributeName == null) continue;
        if (!declaredAttributes.containsKey(attributeName) &&
            !inheritedProperties.contains(attributeName) &&
            !localProperties.containsKey(attributeName) &&
            !isDefinedByProperty(attribute, localProperties.values(), declaredAttributes)) {
          final PyExpression assignedValue = attribute.findAssignedValue();
          if (assignedValue == null) continue;

          if (expressionReferencesLocalName(assignedValue, node)) {
            registerProblem(attribute, PyPsiBundle.message("INSP.attribute.outside.init", attributeName),
                            new AddFieldQuickFix(attributeName, "None", containingClass.getName(), false));
          }
          else {
            registerProblem(attribute, PyPsiBundle.message("INSP.attribute.outside.init", attributeName),
                            new PyMoveAttributeToInitQuickFix(),
                            new AddFieldQuickFix(attributeName, "None", containingClass.getName(), false));
          }
        }
      }
    }
  }

  private static boolean expressionReferencesLocalName(@NotNull PyExpression assignedValue, @NotNull PyFunction function) {
    Collection<PyReferenceExpression> references = PsiTreeUtil.collectElementsOfType(assignedValue, PyReferenceExpression.class);
    for (PyReferenceExpression reference : references) {
      if (reference.isQualified()) continue;
      Collection<PsiElement> resolved = PyResolveUtil.resolveLocally(reference);
      if (resolved.isEmpty()) continue;
      if (ContainerUtil.exists(resolved, it -> it instanceof PyParameter && ((PyParameter)it).isSelf())) continue;
      if (ContainerUtil.exists(resolved, it -> function == ScopeUtil.getScopeOwner(it))) return true;
    }
    return false;
  }

  private static boolean isDefinedByProperty(@NotNull PyTargetExpression attribute,
                                             @NotNull Collection<Property> properties,
                                             @NotNull Map<String, PyTargetExpression> attributesInInit) {
    return StreamEx.of(properties)
                   .filter(it -> isSetBy(attribute, it))
                   .anyMatch(it -> attributesInInit.containsKey(it.getName()));
  }

  private static boolean isApplicable(@NotNull PyClass containingClass, @NotNull TypeEvalContext context) {
    return !PythonUnitTestDetectorsKt.isTestClass(containingClass, context) &&
           !containingClass.isSubclass("django.db.models.base.Model", context);
  }

  @Nullable
  private static Collection<PyTargetExpression> getSetterTargetExpressions(@NotNull Property property) {
    if (!property.getSetter().isDefined() || property.getSetter().value() == null) {
      return null;
    }
    final PyFunction setter = property.getSetter().value().asMethod();
    if (setter == null) {
      return null;
    }
    return StreamEx.of(ControlFlowCache.getScope(setter).getTargetExpressions())
      .filter(PyUtil::isInstanceAttribute)
      .toList();
  }

  /**
   * Check whether the {@code property} sets the {@code attribute} provided.
   */
  private static boolean isSetBy(@NotNull PyTargetExpression attribute, @NotNull Property property) {
    final Collection<PyTargetExpression> propertyTargetExpressions = getSetterTargetExpressions(property);
    return propertyTargetExpressions != null &&
           attribute.getName() != null &&
           StreamEx.of(propertyTargetExpressions)
                   .map(targetExpression -> targetExpression.getName())
                   .nonNull()
                   .anyMatch(name -> name.equals(attribute.getName()));
  }
}
