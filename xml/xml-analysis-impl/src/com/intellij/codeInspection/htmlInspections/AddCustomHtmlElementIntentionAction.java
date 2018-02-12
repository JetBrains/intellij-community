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

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

public class AddCustomHtmlElementIntentionAction implements LocalQuickFix {
  private final String myName;
  private final String myText;
  @NotNull private final Key<HtmlUnknownElementInspection> myInspectionKey;

  public AddCustomHtmlElementIntentionAction(@NotNull Key<HtmlUnknownElementInspection> inspectionKey, String name, String text) {
    myInspectionKey = inspectionKey;
    myName = name;
    myText = text;
  }

  @Override
  @NotNull
  public String getName() {
    return myText;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return XmlBundle.message("fix.html.family");
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();

    InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
    profile.modifyToolSettings(myInspectionKey, element, tool -> tool.addEntry(myName));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
