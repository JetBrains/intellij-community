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

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class XmlSuppressableInspectionTool extends LocalInspectionTool implements BatchSuppressableTool {
  @NonNls static final String ALL = "ALL";

  public static SuppressQuickFix[] getSuppressFixes(final String shortName) {
    return getSuppressFixes(shortName, new DefaultXmlSuppressionProvider());
  }

  public static SuppressQuickFix[] getSuppressFixes(final String shortName, XmlSuppressionProvider provider) {
    final String id = HighlightDisplayKey.find(shortName).getID();
    return new SuppressQuickFix[]{new SuppressTagStatic(id, provider), new SuppressForFile(id, provider), new SuppressAllForFile(provider)};
  }

  public static abstract class XmlSuppressFix implements SuppressQuickFix {

    protected final String myId;
    protected final XmlSuppressionProvider myProvider;

    protected XmlSuppressFix(String inspectionId, XmlSuppressionProvider suppressionProvider) {
      myId = inspectionId;
      myProvider = suppressionProvider;
    }

    protected XmlSuppressFix(String id) {
      this(id, new DefaultXmlSuppressionProvider());
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

  public static class SuppressTagStatic extends XmlSuppressFix {

    public SuppressTagStatic(String inspectionId, XmlSuppressionProvider suppressionProvider) {
      super(inspectionId, suppressionProvider);
    }

    public SuppressTagStatic(String id) {
      super(id);
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("xml.suppressable.for.tag.title");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (PsiTreeUtil.getParentOfType(element, XmlTag.class) == null) return;
      myProvider.suppressForTag(element, myId);
    }

  }

  public static class SuppressForFile extends XmlSuppressFix {

    public SuppressForFile(String inspectionId, XmlSuppressionProvider suppressionProvider) {
      super(inspectionId, suppressionProvider);
    }

    public SuppressForFile(String id) {
      super(id);
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
      myProvider.suppressForFile(element, myId);
    }
  }

  public static class SuppressAllForFile extends SuppressForFile {
    public SuppressAllForFile(XmlSuppressionProvider provider) {
      super(ALL, provider);
    }

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
