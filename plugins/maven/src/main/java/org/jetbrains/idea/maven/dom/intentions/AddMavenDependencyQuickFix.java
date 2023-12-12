/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchDialog;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AddMavenDependencyQuickFix implements IntentionAction, LowPriorityAction {

  private static final Pattern CLASSNAME_PATTERN =
    Pattern.compile("(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\.)*\\p{Lu}\\p{javaJavaIdentifierPart}+");

  private final PsiJavaCodeReferenceElement myRef;

  public AddMavenDependencyQuickFix(PsiJavaCodeReferenceElement ref) {
    myRef = ref;
  }

  @Override
  @NotNull
  public String getText() {
    return MavenDomBundle.message("fix.add.dependency");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return MavenDomBundle.message("inspection.group");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myRef.isValid() && MavenDomUtil.findContainingProject(file) != null && looksLikeClassName(getReferenceText());
  }

  private static boolean looksLikeClassName(@Nullable String text) {
    if (text == null) return false;
    //if (true) return true;
    return CLASSNAME_PATTERN.matcher(text).matches();
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!myRef.isValid()) return;

    MavenProject mavenProject = MavenDomUtil.findContainingProject(file);
    if (mavenProject == null) return;

    final List<MavenId> ids = MavenArtifactSearchDialog.searchForClass(project, getReferenceText());
    if (ids.isEmpty()) return;

    final MavenDomProjectModel model = MavenDomUtil.getMavenDomProjectModel(project, mavenProject.getFile());
    if (model == null) return;

    WriteCommandAction.writeCommandAction(project, DomUtil.getFile(model)).withName(
      MavenDomBundle.message("maven.dom.quickfix.add.maven.dependency")).run(() -> {
      boolean isTestSource = false;

      VirtualFile virtualFile = file.getOriginalFile().getVirtualFile();
      if (virtualFile != null) {
        isTestSource = ProjectRootManager.getInstance(project).getFileIndex().isInTestSourceContent(virtualFile);
      }

      Map<MavenId, MavenDomDependency> dependencyMap = model.getDependencies().getDependencies().stream().collect(Collectors.toMap(
        it -> new MavenId(it.getGroupId().getStringValue(), it.getArtifactId().getStringValue(), it.getVersion().getStringValue()),
        Function.identity(),
        (dep1, dep2) -> {
          return dep1;
        }
      ));

      for (MavenId each : ids) {
        MavenDomDependency existingDependency = dependencyMap.get(each);
        if (existingDependency == null) {
          MavenDomDependency dependency = MavenDomUtil.createDomDependency(model, null, each);
          if (isTestSource) {
            dependency.getScope().setStringValue("test");
          }
        }
        else {
          if ("test".equals(existingDependency.getScope().getStringValue()) && !isTestSource) {
            existingDependency.getScope().setValue(null);
          }
        }
      }
    });

    FileDocumentManager.getInstance().saveAllDocuments();
    MavenProjectsManager.getInstance(project).forceUpdateAllProjectsOrFindAllAvailablePomFiles();
  }

  public String getReferenceText() {
    PsiJavaCodeReferenceElement result = myRef;
    while (true) {
      PsiElement parent = result.getParent();
      if (!(parent instanceof PsiJavaCodeReferenceElement)) {
        break;
      }

      result = (PsiJavaCodeReferenceElement)parent;
    }

    return result.getQualifiedName();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
