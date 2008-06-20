package org.jetbrains.idea.maven.dom;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.dom.model.MavenModel;
import org.jetbrains.idea.maven.dom.model.MavenParent;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.repository.MavenIndexException;
import org.jetbrains.idea.maven.repository.MavenIndicesManager;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class MavenParentRelativePathConverter extends MavenArtifactConverter {
  @Override
  protected boolean isValid(MavenIndicesManager manager, MavenId id, String specifiedPath) throws MavenIndexException {
    return LocalFileSystem.getInstance().findFileByPath(specifiedPath) != null;
  }

  @NotNull
  @Override
  public Collection<String> getVariants(ConvertContext context) {
    List<String> result = new ArrayList<String>();
    VirtualFile currentFile = context.getFile().getOriginalFile().getVirtualFile();
    for (DomFileElement<MavenModel> each : PomDescriptor.collectProjectPoms(context.getFile().getProject())) {
      VirtualFile file = each.getOriginalFile().getVirtualFile();
      if (file == currentFile) continue;
      result.add(MavenDomUtil.calcRelativePath(currentFile.getParent(), file));
    }
    return result;
  }

  @Override
  protected Set<String> getVariants(MavenIndicesManager manager, MavenId id) throws MavenIndexException {
    throw new UnsupportedOperationException();
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
      MavenParent parent = getMavenParent(myContext);
      MavenId id = getId(myContext);

      MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
      MavenProjectModel mavenProject = manager.findProject(id);
      if (mavenProject != null) {
        VirtualFile currentFile = myContext.getFile().getVirtualFile();
        el.setStringValue(MavenDomUtil.calcRelativePath(currentFile.getParent(), mavenProject.getFile()));
      }
    }
  }
}