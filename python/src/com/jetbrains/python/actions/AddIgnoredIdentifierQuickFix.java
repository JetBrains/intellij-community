package com.jetbrains.python.actions;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.jetbrains.python.inspections.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class AddIgnoredIdentifierQuickFix implements LocalQuickFix, LowPriorityAction {
  public static final String END_WILDCARD = ".*";

  @NotNull private final PyQualifiedName myIdentifier;
  private final boolean myIgnoreAllAttributes;

  public AddIgnoredIdentifierQuickFix(@NotNull PyQualifiedName identifier, boolean ignoreAllAttributes) {
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
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PyUnresolvedReferencesInspection instance = PyUnresolvedReferencesInspection.getInstance(descriptor.getPsiElement());
    String name = myIdentifier.toString();
    if (myIgnoreAllAttributes) {
      name = name + END_WILDCARD;
    }
    if (!instance.ignoredIdentifiers.contains(name)) {
      instance.ignoredIdentifiers.add(name);
      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
      InspectionProfileManager.getInstance().fireProfileChanged(profile);
    }
  }
}
