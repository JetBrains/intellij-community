// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ThreeState;
import com.intellij.xml.psi.XmlPsiBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class XmlSuppressableInspectionTool extends LocalInspectionTool implements BatchSuppressableTool {
  static final @NonNls String ALL = "ALL";

  public static SuppressQuickFix @NotNull [] getSuppressFixes(@NotNull String toolId) {
    return getSuppressFixes(toolId, new DefaultXmlSuppressionProvider());
  }

  public static SuppressQuickFix @NotNull [] getSuppressFixes(@NotNull String toolId, @NotNull XmlSuppressionProvider provider) {
    return new SuppressQuickFix[]{new SuppressTagStatic(toolId, provider), new SuppressForFile(toolId, provider),
      new SuppressAllForFile(provider)};
  }

  public abstract static class XmlSuppressFix implements InjectionAwareSuppressQuickFix, ContainerBasedSuppressQuickFix {

    protected final String myId;
    @SafeFieldForPreview protected final XmlSuppressionProvider myProvider;
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
    public @NotNull String getFamilyName() {
      return getName();
    }
    
    @Override
    public @Nullable PsiElement getContainer(@Nullable PsiElement context) {
      return null;
    }

    @Override
    public @NotNull ThreeState isShouldBeAppliedToInjectionHost() {
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

    @Override
    public @NotNull String getName() {
      return XmlPsiBundle.message("xml.suppressable.for.tag.title");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (PsiTreeUtil.getParentOfType(element, XmlTag.class) == null) return;
      myProvider.suppressForTag(element, myId);
    }
    
    @Override
    public @Nullable PsiElement getContainer(@Nullable PsiElement context) {
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

    @Override
    public @NotNull String getName() {
      return XmlPsiBundle.message("xml.suppressable.for.file.title");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      myProvider.suppressForFile(element, myId);
    }
    
    @Override
    public @Nullable PsiElement getContainer(@Nullable PsiElement context) {
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

    @Override
    public @NotNull String getName() {
      return XmlPsiBundle.message("xml.suppressable.all.for.file.title");
    }
  }
}
