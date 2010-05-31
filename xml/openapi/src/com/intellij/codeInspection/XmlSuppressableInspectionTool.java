/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInspection;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.*;
import com.intellij.util.*;
import org.jetbrains.annotations.*;

public abstract class XmlSuppressableInspectionTool extends LocalInspectionTool implements CustomSuppressableInspectionTool {
  @NonNls static final String ALL = "ALL";

  public SuppressIntentionAction[] getSuppressActions(final PsiElement element) {
    return new SuppressIntentionAction[]{new SuppressTag(), new SuppressForFile(getID()), new SuppressAllForFile()};
  }

  public boolean isSuppressedFor(final PsiElement element) {
    return XmlSuppressionProvider.isSuppressed(element, getID());
  }

  public class SuppressTag extends SuppressIntentionAction {

    @NotNull
    public String getText() {
      return InspectionsBundle.message("xml.suppressable.for.tag.title");
    }

    @NotNull
    public String getFamilyName() {
      return getText();
    }

    public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
      return PsiTreeUtil.getParentOfType(element, XmlTag.class) != null;
    }

    public void invoke(final Project project, final Editor editor, final PsiElement element) throws IncorrectOperationException {
      XmlSuppressionProvider.getProvider(element.getContainingFile()).suppressForTag(element, getID());
    }

    public boolean startInWriteAction() {
      return true;
    }
  }

  public static class SuppressForFile extends SuppressIntentionAction {
    private final String myInspectionId;

    public SuppressForFile(final String inspectionId) {
      myInspectionId = inspectionId;
    }

    @NotNull
    public String getText() {
      return InspectionsBundle.message("xml.suppressable.for.file.title");
    }

    @NotNull
    public String getFamilyName() {
      return getText();
    }

    public void invoke(final Project project, final Editor editor, final PsiElement element) throws IncorrectOperationException {
      XmlSuppressionProvider.getProvider(element.getContainingFile()).suppressForFile(element, myInspectionId);
    }

    public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
      return  element.isValid() && element.getContainingFile() instanceof XmlFile;
    }


    public boolean startInWriteAction() {
      return true;
    }
  }

  public static class SuppressAllForFile extends SuppressForFile {

    public SuppressAllForFile() {
      super(ALL);
    }

    @NotNull
    public String getText() {
      return InspectionsBundle.message("xml.suppressable.all.for.file.title");
    }
  }
}
