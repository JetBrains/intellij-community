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
package org.jetbrains.idea.maven.dom.annotator;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
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

  private void addProblems(DomElement element, MavenDomProjectModel model, DomElementAnnotationHolder holder,
                           MavenProjectProblem.ProblemType... types) {
    MavenProject mavenProject = MavenDomUtil.findProject(model);
    if (mavenProject != null) {
      for (MavenProjectProblem each : mavenProject.getProblems()) {
        MavenProjectProblem.ProblemType type = each.getType();
        if (!Arrays.asList(types).contains(type)) continue;
        VirtualFile problemFile = LocalFileSystem.getInstance().findFileByPath(each.getPath());

        LocalQuickFix[] fixes = LocalQuickFix.EMPTY_ARRAY;
        if (problemFile != null && mavenProject.getFile() != problemFile) {
          fixes = new LocalQuickFix[]{new OpenProblemFileFix(problemFile)};
        }
        holder.createProblem(element, HighlightSeverity.ERROR, each.getDescription(), fixes);
      }
    }
  }

  private static class OpenProblemFileFix implements LocalQuickFix {
    private final VirtualFile myFile;

    private OpenProblemFileFix(VirtualFile file) {
      myFile = file;
    }

    @NotNull
    public String getName() {
      return MavenDomBundle.message("fix.open.file", myFile.getName());
    }

    @NotNull
    public String getFamilyName() {
      return MavenDomBundle.message("inspection.group");
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      new OpenFileDescriptor(project, myFile).navigate(true);
    }
  }
}
