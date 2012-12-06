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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class XmlSuppressableInspectionTool extends LocalInspectionTool implements CustomSuppressableInspectionTool {
  @NonNls static final String ALL = "ALL";

  @Override
  public SuppressIntentionAction[] getSuppressActions(final PsiElement element) {
    return new SuppressIntentionAction[]{new SuppressTag(), new SuppressForFile(getID()), new SuppressAllForFile()};
  }

  @Override
  public boolean isSuppressedFor(@NotNull final PsiElement element) {
    return XmlSuppressionProvider.isSuppressed(element, getID());
  }

  public class SuppressTag extends SuppressTagStatic {

    public SuppressTag() {
      super(getID());
    }
  }

  public static class SuppressTagStatic extends SuppressIntentionAction {
    private final String id;

    public SuppressTagStatic(@NotNull String id) {
      this.id = id;
    }

    @Override
    @NotNull
    public String getText() {
      return InspectionsBundle.message("xml.suppressable.for.tag.title");
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getText();
    }

    @Override
    public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
      return PsiTreeUtil.getParentOfType(element, XmlTag.class) != null;
    }

    @Override
    public void invoke(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) throws IncorrectOperationException {
      XmlSuppressionProvider.getProvider(element.getContainingFile()).suppressForTag(element, id);
    }
  }

  public static class SuppressForFile extends SuppressIntentionAction {
    private final String myInspectionId;

    public SuppressForFile(@NotNull String inspectionId) {
      myInspectionId = inspectionId;
    }

    @Override
    @NotNull
    public String getText() {
      return InspectionsBundle.message("xml.suppressable.for.file.title");
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getText();
    }

    @Override
    public void invoke(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) throws IncorrectOperationException {
      XmlSuppressionProvider.getProvider(element.getContainingFile()).suppressForFile(element, myInspectionId);
    }

    @Override
    public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
      return  element.isValid() && element.getContainingFile() instanceof XmlFile;
    }
  }

  public static class SuppressAllForFile extends SuppressForFile {

    public SuppressAllForFile() {
      super(ALL);
    }

    @Override
    @NotNull
    public String getText() {
      return InspectionsBundle.message("xml.suppressable.all.for.file.title");
    }
  }
}
