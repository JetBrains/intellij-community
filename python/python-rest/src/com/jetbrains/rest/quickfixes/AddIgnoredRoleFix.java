package com.jetbrains.rest.quickfixes;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.jetbrains.rest.RestBundle;
import com.jetbrains.rest.inspections.RestRoleInspection;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class AddIgnoredRoleFix implements LocalQuickFix, LowPriorityAction {
  private final String myRole;
  private RestRoleInspection myInspection;

  public AddIgnoredRoleFix(String role, RestRoleInspection visitor) {
    myRole = role;
    myInspection = visitor;
  }

  @NotNull
  @Override
  public String getName() {
    return RestBundle.message("QFIX.ignore.role", myRole);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Ignore undefined role";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (!myInspection.ignoredRoles.contains(myRole)) {
      myInspection.ignoredRoles.add(myRole);
      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
      InspectionProfileManager.getInstance().fireProfileChanged(profile);
    }
  }
}
