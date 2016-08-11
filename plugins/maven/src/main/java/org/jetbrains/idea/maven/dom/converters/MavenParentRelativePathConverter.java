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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MavenParentRelativePathConverter extends ResolvingConverter<PsiFile> implements CustomReferenceConverter {
  @Override
  public PsiFile fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (StringUtil.isEmptyOrSpaces(s)) return null;

    VirtualFile contextFile = context.getFile().getVirtualFile();
    if (contextFile == null) return null;

    VirtualFile f = contextFile.getParent().findFileByRelativePath(s);
    if (f == null) return null;

    if (f.isDirectory()) f = f.findChild(MavenConstants.POM_XML);
    if (f == null) return null;

    return context.getPsiManager().findFile(f);
  }

  @Override
  public String toString(@Nullable PsiFile f, ConvertContext context) {
    if (f == null) return null;
    VirtualFile currentFile = context.getFile().getOriginalFile().getVirtualFile();
    if (currentFile == null) return null;

    return MavenDomUtil.calcRelativePath(currentFile.getParent(), f.getVirtualFile());
  }

  @NotNull
  @Override
  public Collection<PsiFile> getVariants(ConvertContext context) {
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
  public LocalQuickFix[] getQuickFixes(ConvertContext context) {
    return ArrayUtil.append(super.getQuickFixes(context), new RelativePathFix(context));
  }

  private static class RelativePathFix implements LocalQuickFix {
    private final ConvertContext myContext;

    public RelativePathFix(ConvertContext context) {
      myContext = context;
    }

    @NotNull
    public String getName() {
      return MavenDomBundle.message("fix.parent.path");
    }

    @NotNull
    public String getFamilyName() {
      return MavenDomBundle.message("inspection.group");
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      GenericDomValue el = (GenericDomValue)myContext.getInvocationElement();
      MavenId id = MavenArtifactCoordinatesHelper.getId(myContext);

      MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
      MavenProject parentFile = manager.findProject(id);
      if (parentFile != null) {
        VirtualFile currentFile = myContext.getFile().getVirtualFile();
        el.setStringValue(MavenDomUtil.calcRelativePath(currentFile.getParent(), parentFile.getFile()));
      }
    }
  }

  @NotNull
  public PsiReference[] createReferences(final GenericDomValue genericDomValue, final PsiElement element, final ConvertContext context) {
    return new MavenPathReferenceConverter(item -> item.isDirectory() || item.getName().equals("pom.xml")).createReferences(genericDomValue, element, context);
  }
}