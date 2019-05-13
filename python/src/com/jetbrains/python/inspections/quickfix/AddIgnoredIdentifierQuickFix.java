/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModelKt;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class AddIgnoredIdentifierQuickFix implements LocalQuickFix, LowPriorityAction {
  public static final String END_WILDCARD = ".*";

  @NotNull private final QualifiedName myIdentifier;
  private final boolean myIgnoreAllAttributes;

  public AddIgnoredIdentifierQuickFix(@NotNull QualifiedName identifier, boolean ignoreAllAttributes) {
    myIdentifier = identifier;
    myIgnoreAllAttributes = ignoreAllAttributes;
  }

  @NotNull
  @Override
  public String getName() {
    if (myIgnoreAllAttributes) {
      return "Mark all unresolved attributes of '" + myIdentifier + "' as ignored";
    }
    else {
      return "Ignore unresolved reference '" + myIdentifier + "'";
    }
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Ignore unresolved reference";
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement context = descriptor.getPsiElement();
    InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile(project, model -> {
      PyUnresolvedReferencesInspection inspection =
        (PyUnresolvedReferencesInspection)model.getUnwrappedTool(PyUnresolvedReferencesInspection.class.getSimpleName(), context);
      String name = myIdentifier.toString();
      if (myIgnoreAllAttributes) {
        name += END_WILDCARD;
      }
      assert inspection != null;
      if (!inspection.ignoredIdentifiers.contains(name)) {
        inspection.ignoredIdentifiers.add(name);
      }
    });
  }
}
