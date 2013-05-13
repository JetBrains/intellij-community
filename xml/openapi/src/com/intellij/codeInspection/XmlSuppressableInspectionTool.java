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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class XmlSuppressableInspectionTool extends LocalInspectionTool implements BatchSuppressableTool {
  @NonNls static final String ALL = "ALL";

  @NotNull
  @Override
  public SuppressQuickFix[] getBatchSuppressActions(@Nullable PsiElement element) {
    return new SuppressQuickFix[]{new SuppressTag(), new SuppressForFile(getID()), new SuppressAllForFile()};
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

  public static class SuppressTagStatic implements SuppressQuickFix {
    private final String id;

    public SuppressTagStatic(@NotNull String id) {
      this.id = id;
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("xml.suppressable.for.tag.title");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, @NotNull PsiElement context) {
      return context.isValid();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (PsiTreeUtil.getParentOfType(element, XmlTag.class) == null) return;
      XmlSuppressionProvider.getProvider(element.getContainingFile()).suppressForTag(element, id);
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }
  }

  public static class SuppressForFile implements SuppressQuickFix {
    private final String myInspectionId;

    public SuppressForFile(@NotNull String inspectionId) {
      myInspectionId = inspectionId;
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("xml.suppressable.for.file.title");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element == null || !element.isValid() || !(element.getContainingFile() instanceof XmlFile)) return;
      XmlSuppressionProvider.getProvider(element.getContainingFile()).suppressForFile(element, myInspectionId);
    }

    @Override
    public boolean isAvailable(@NotNull Project project, @NotNull PsiElement context) {
      return context.isValid();
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }
  }

  public static class SuppressAllForFile extends SuppressForFile {
    public SuppressAllForFile() {
      super(ALL);
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("xml.suppressable.all.for.file.title");
    }
  }
}
