/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class XmlSuppressableInspectionTool extends LocalInspectionTool implements BatchSuppressableTool {
  @NonNls static final String ALL = "ALL";

  @NotNull
  public static SuppressQuickFix[] getSuppressFixes(@NotNull String toolId) {
    return getSuppressFixes(toolId, new DefaultXmlSuppressionProvider());
  }

  @NotNull
  public static SuppressQuickFix[] getSuppressFixes(@NotNull String toolId, @NotNull XmlSuppressionProvider provider) {
    return new SuppressQuickFix[]{new SuppressTagStatic(toolId, provider), new SuppressForFile(toolId, provider),
      new SuppressAllForFile(provider)};
  }

  public abstract static class XmlSuppressFix implements InjectionAwareSuppressQuickFix, ContainerBasedSuppressQuickFix {

    protected final String myId;
    protected final XmlSuppressionProvider myProvider;
    private ThreeState myShouldBeAppliedToInjectionHost = ThreeState.UNSURE;

    protected XmlSuppressFix(String inspectionId, XmlSuppressionProvider suppressionProvider) {
      myId = inspectionId;
      myProvider = suppressionProvider;
    }

    protected XmlSuppressFix(String id) {
      this(id, new DefaultXmlSuppressionProvider());
    }

    @Override
    public boolean isAvailable(@NotNull Project project, @NotNull PsiElement context) {
      return context.isValid() && getContainer(context) != null;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }
    
    @Nullable
    @Override
    public PsiElement getContainer(@Nullable PsiElement context) {
      return null;
    }

    @NotNull
    @Override
    public ThreeState isShouldBeAppliedToInjectionHost() {
      return myShouldBeAppliedToInjectionHost;
    }

    @Override
    public void setShouldBeAppliedToInjectionHost(@NotNull ThreeState shouldBeAppliedToInjectionHost) {
      myShouldBeAppliedToInjectionHost = shouldBeAppliedToInjectionHost;
    }

    @Override
    public boolean isSuppressAll() {
      return false;
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
    
    @Nullable
    @Override
    public PsiElement getContainer(@Nullable PsiElement context) {
      return PsiTreeUtil.getParentOfType(context, XmlTag.class);
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
      PsiElement container = getContainer(element);
      if (container instanceof XmlFile) {
        myProvider.suppressForFile(element, myId);
      }
    }
    
    @Nullable
    @Override
    public PsiElement getContainer(@Nullable PsiElement context) {
      return context == null || !context.isValid() ? null : context.getContainingFile();
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
