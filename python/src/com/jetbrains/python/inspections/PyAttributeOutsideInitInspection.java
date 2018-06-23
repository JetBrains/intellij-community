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
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.ThreeState;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.inspections.quickfix.PyMoveAttributeToInitQuickFix;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.testing.PythonUnitTestUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: ktisha
 *
 * Inspection to detect situations, where instance attribute is defined outside __init__ function.
 */
public class PyAttributeOutsideInitInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.attribute.outside.init");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }


  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyFunction(PyFunction node) {
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

      final List<PyTargetExpression> classAttributes = containingClass.getClassAttributes();
      final Map<String, Property> properties = containingClass.getProperties();
      final Map<String, PyTargetExpression> attributesInInit = new HashMap<>();

      StreamEx.of(classAttributes)
              .filter(attribute -> !properties.containsKey(attribute.getName()))
              .forEach(attribute -> attributesInInit.put(attribute.getName(), attribute));

      final PyFunction initMethod = containingClass.findMethodByName(PyNames.INIT, false, null);
      if (initMethod != null) {
        PyClassImpl.collectInstanceAttributes(initMethod, attributesInInit);
      }
      for (PyClass superClass : containingClass.getAncestorClasses(myTypeEvalContext)) {
        final PyFunction superInit = superClass.findMethodByName(PyNames.INIT, false, null);
        if (superInit != null) {
          PyClassImpl.collectInstanceAttributes(superInit, attributesInInit);
        }

        for (PyTargetExpression classAttr : superClass.getClassAttributes()) {
          attributesInInit.put(classAttr.getName(), classAttr);
        }
      }

      final Map<String, PyTargetExpression> attributes = new HashMap<>();
      PyClassImpl.collectInstanceAttributes(node, attributes);

      for (PyTargetExpression attribute : attributes.values()) {
        final String attributeName = attribute.getName();
        if (attributeName == null) continue;
        if (!attributesInInit.containsKey(attributeName) &&
            !isDefinedByProperty(attribute, properties.values(), attributesInInit)) {
          registerProblem(attribute, PyBundle.message("INSP.attribute.$0.outside.init", attributeName),
                          new PyMoveAttributeToInitQuickFix());
        }
      }
    }
  }

  private static boolean isDefinedByProperty(@NotNull PyTargetExpression attribute,
                                             @NotNull Collection<Property> properties,
                                             @NotNull Map<String, PyTargetExpression> attributesInInit) {
    return StreamEx.of(properties)
                   .filter(it -> isSetBy(attribute, it))
                   .anyMatch(it -> attributesInInit.containsKey(it.getName()));
  }

  private static boolean isApplicable(@NotNull PyClass containingClass, @NotNull TypeEvalContext context) {
    return !PythonUnitTestUtil.isTestClass(containingClass, ThreeState.UNSURE, context) &&
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
    return ControlFlowCache.getScope(setter).getTargetExpressions();
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
