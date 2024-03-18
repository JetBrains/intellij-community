// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import org.jetbrains.annotations.NotNull;


public class AddIgnoredIdentifierQuickFix extends ModCommandQuickFix implements LowPriorityAction {

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
      return PyBundle.message("QFIX.mark.all.unresolved.attributes.of.0.as.ignored", myIdentifier);
    }
    else {
      return PyBundle.message("QFIX.ignore.unresolved.reference.0", myIdentifier);
    }
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return PyBundle.message("QFIX.ignore.unresolved.reference");
  }

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement context = descriptor.getPsiElement();
    return ModCommand.updateInspectionOption(context, new PyUnresolvedReferencesInspection(), inspection -> {
      String name = myIdentifier.toString();
      if (myIgnoreAllAttributes) {
        name += PyNames.END_WILDCARD;
      }
      if (!inspection.ignoredIdentifiers.contains(name)) {
        inspection.ignoredIdentifiers.add(name);
      }
    });
  }
}
