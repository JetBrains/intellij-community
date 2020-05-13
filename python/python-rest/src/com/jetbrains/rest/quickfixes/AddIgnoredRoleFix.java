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
package com.jetbrains.rest.quickfixes;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.jetbrains.rest.RestBundle;
import com.jetbrains.rest.inspections.RestRoleInspection;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class AddIgnoredRoleFix implements LocalQuickFix, LowPriorityAction {
  private final String myRole;
  private final RestRoleInspection myInspection;

  public AddIgnoredRoleFix(String role, RestRoleInspection visitor) {
    myRole = role;
    myInspection = visitor;
  }

  @NotNull
  @Override
  public String getName() {
    return RestBundle.message("QFIX.ignore.role.0", myRole);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return RestBundle.message("QFIX.ignore.role");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (!myInspection.ignoredRoles.contains(myRole)) {
      myInspection.ignoredRoles.add(myRole);
      ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
    }
  }
}
