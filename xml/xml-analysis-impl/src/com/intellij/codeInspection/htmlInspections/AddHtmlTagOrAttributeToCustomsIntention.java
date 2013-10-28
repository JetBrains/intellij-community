/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class AddHtmlTagOrAttributeToCustomsIntention implements IntentionAction {
  private final String myName;
  private final String myText;
  private final Key<InspectionProfileEntry> myInspectionKey;

  public AddHtmlTagOrAttributeToCustomsIntention(Key<InspectionProfileEntry> inspectionKey, String name, String text) {
    myInspectionKey = inspectionKey;
    myName = name;
    myText = text;
  }

  @Override
  @NotNull
  public String getText() {
    return myText;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return XmlBundle.message("fix.html.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, final PsiFile file) throws IncorrectOperationException {
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    profile.modifyToolSettings(myInspectionKey, file, new Consumer<InspectionProfileEntry>() {
      @Override
      public void consume(InspectionProfileEntry entry) {
        XmlEntitiesInspection xmlEntitiesInspection = (XmlEntitiesInspection) entry;
        xmlEntitiesInspection.addEntry(myName);
      }
    });
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
