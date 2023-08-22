// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.annotator;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomParent;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.model.MavenProjectProblem;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.Arrays;

public class MavenDomAnnotator implements DomElementsAnnotator {
  @Override
  public void annotate(DomElement element, DomElementAnnotationHolder holder) {
    if (element instanceof MavenDomProjectModel) {
      addProblems(element, (MavenDomProjectModel)element, holder,
                  MavenProjectProblem.ProblemType.STRUCTURE,
                  MavenProjectProblem.ProblemType.SETTINGS_OR_PROFILES);
    }
    else if (element instanceof MavenDomParent) {
      addProblems(element, DomUtil.getParentOfType(element, MavenDomProjectModel.class, true), holder,
                  MavenProjectProblem.ProblemType.PARENT);
    }
  }

  private static void addProblems(DomElement element, MavenDomProjectModel model, DomElementAnnotationHolder holder,
                                  MavenProjectProblem.ProblemType... types) {
    MavenProject mavenProject = MavenDomUtil.findProject(model);
    if (mavenProject != null) {
      for (MavenProjectProblem each : mavenProject.getProblems()) {
        MavenProjectProblem.ProblemType type = each.getType();
        if (!Arrays.asList(types).contains(type)) continue;
        VirtualFile problemFile = LocalFileSystem.getInstance().findFileByPath(each.getPath());

        LocalQuickFix[] fixes = LocalQuickFix.EMPTY_ARRAY;
        if (problemFile != null && !Comparing.equal(mavenProject.getFile(), problemFile)) {
          fixes = new LocalQuickFix[]{new OpenProblemFileFix(problemFile)};
        }
        holder.createProblem(element, HighlightSeverity.ERROR, each.getDescription(), fixes);
      }
    }
  }

  private static final class OpenProblemFileFix implements LocalQuickFix {
    private final VirtualFile myFile;

    private OpenProblemFileFix(VirtualFile file) {
      myFile = file;
    }

    @Override
    @NotNull
    public String getName() {
      return MavenDomBundle.message("fix.open.file", myFile.getName());
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return MavenDomBundle.message("inspection.group");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiNavigationSupport.getInstance().createNavigatable(project, myFile, -1).navigate(true);
    }
  }
}
