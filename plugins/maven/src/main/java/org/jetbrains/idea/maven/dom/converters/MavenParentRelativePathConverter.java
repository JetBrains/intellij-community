// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.references.MavenPathReferenceConverter;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MavenParentRelativePathConverter extends ResolvingConverter<PsiFile> implements CustomReferenceConverter {

  public static final String DEFAULT_PARENT_PATH = "../pom.xml";

  @Override
  public PsiFile fromString(@Nullable @NonNls String s, @NotNull ConvertContext context) {
    String path = s == null ? DEFAULT_PARENT_PATH : s;
    if (StringUtil.isEmptyOrSpaces(path)) return null;

    VirtualFile contextFile = context.getFile().getVirtualFile();
    if (contextFile == null) return null;

    VirtualFile parent = contextFile.getParent();
    if (parent == null) {
      return null;
    }
    VirtualFile f = parent.findFileByRelativePath(path);
    if (f == null) return null;

    if (f.isDirectory()) f = f.findChild(MavenConstants.POM_XML);
    if (f == null) return null;

    return context.getPsiManager().findFile(f);
  }

  @Override
  public String toString(@Nullable PsiFile f, @NotNull ConvertContext context) {
    if (f == null) return null;
    VirtualFile currentFile = context.getFile().getOriginalFile().getVirtualFile();
    if (currentFile == null) return null;

    return MavenDomUtil.calcRelativePath(currentFile.getParent(), f.getVirtualFile());
  }

  @Override
  public @NotNull Collection<PsiFile> getVariants(@NotNull ConvertContext context) {
    List<PsiFile> result = new ArrayList<>();
    PsiFile currentFile = context.getFile().getOriginalFile();
    for (DomFileElement<MavenDomProjectModel> each : MavenDomUtil.collectProjectModels(context.getFile().getProject())) {
      PsiFile file = each.getOriginalFile();
      if (file == currentFile) continue;
      result.add(file);
    }
    return result;
  }

  @Override
  public LocalQuickFix[] getQuickFixes(@NotNull ConvertContext context) {
    return ArrayUtil.append(super.getQuickFixes(context), new RelativePathFix(context));
  }

  private static class RelativePathFix implements LocalQuickFix {
    @SafeFieldForPreview
    private final ConvertContext myContext;

    RelativePathFix(ConvertContext context) {
      myContext = context;
    }

    @Override
    public @NotNull String getName() {
      return MavenDomBundle.message("fix.parent.path");
    }

    @Override
    public @NotNull String getFamilyName() {
      return MavenDomBundle.message("inspection.group");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      MavenId id = MavenArtifactCoordinatesHelper.getId(myContext);

      MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
      MavenProject parentFile = manager.findProject(id);
      if (parentFile != null) {
        VirtualFile currentFile = myContext.getFile().getVirtualFile();
        String relativePath = MavenDomUtil.calcRelativePath(currentFile.getParent(), parentFile.getFile());
        var xmlTag = (XmlTag)descriptor.getPsiElement();
        xmlTag.getValue().setText(relativePath);
      }
    }
  }

  @Override
  public PsiReference @NotNull [] createReferences(final GenericDomValue genericDomValue, final PsiElement element, final ConvertContext context) {
    Project project = element.getProject();
    Condition<PsiFileSystemItem> condition = item ->
      item.isDirectory() || MavenUtil.isPomFile(project, item.getVirtualFile());
    return new MavenPathReferenceConverter(condition).createReferences(genericDomValue, element, context);
  }
}