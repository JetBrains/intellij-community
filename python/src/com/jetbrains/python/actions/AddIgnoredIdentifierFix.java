package com.jetbrains.python.actions;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.jetbrains.python.inspections.PyUnresolvedReferencesInspection;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class AddIgnoredIdentifierFix implements LocalQuickFix, LowPriorityAction {
  private final String myIdentifier;

  public AddIgnoredIdentifierFix(String identifier) {
    myIdentifier = identifier;
  }

  @NotNull
  @Override
  public String getName() {
    return "Ignore unresolved identifier " + myIdentifier;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Ignore unresolved identifier";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PyUnresolvedReferencesInspection instance = PyUnresolvedReferencesInspection.getInstance(descriptor.getPsiElement());
    if (!instance.ignoredIdentifiers.contains(myIdentifier)) {
      instance.ignoredIdentifiers.add(myIdentifier);
      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
      InspectionProfileManager.getInstance().fireProfileChanged(profile);
    }
  }
}
