package org.jetbrains.idea.maven.dom;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenModel;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MavenParentRelativePathConverter extends ResolvingConverter<PsiFile> {
  @Override
  public PsiFile fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s == null) return null;
    s = PropertyResolver.resolve(s, context.getInvocationElement().<MavenModel>getRoot());
    VirtualFile f = context.getFile().getVirtualFile().getParent().findFileByRelativePath(s);
    if (f == null) return null;
    return PsiManager.getInstance(context.getXmlElement().getProject()).findFile(f);
  }

  @Override
  public String toString(@Nullable PsiFile f, ConvertContext context) {
    if (f == null) return null;
    PsiFile currentPsiFile = context.getFile().getOriginalFile();
    if (currentPsiFile == null) currentPsiFile = context.getFile();
    VirtualFile currentFile = currentPsiFile.getVirtualFile();
    return MavenDomUtil.calcRelativePath(currentFile.getParent(), f.getVirtualFile());
  }

  @NotNull
  @Override
  public Collection<PsiFile> getVariants(ConvertContext context) {
    List<PsiFile> result = new ArrayList<PsiFile>();
    PsiFile currentFile = context.getFile().getOriginalFile();
    for (DomFileElement<MavenModel> each : PomDescriptor.collectProjectPoms(context.getFile().getProject())) {
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

  private class RelativePathFix implements LocalQuickFix {
    private ConvertContext myContext;

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
      MavenId id = MavenArtifactConverterHelper.getId(myContext);

      MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
      MavenProjectModel parentFile = manager.findProject(id);
      if (parentFile != null) {
        VirtualFile currentFile = myContext.getFile().getVirtualFile();
        el.setStringValue(MavenDomUtil.calcRelativePath(currentFile.getParent(), parentFile.getFile()));
      }
    }
  }
}