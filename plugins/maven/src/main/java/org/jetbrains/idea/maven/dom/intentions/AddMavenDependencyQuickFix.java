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

import java.util.List;
import java.util.regex.Pattern;

public class AddMavenDependencyQuickFix implements IntentionAction, LowPriorityAction {

  private static final Pattern CLASSNAME_PATTERN = Pattern.compile("(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\.)*\\p{Lu}\\p{javaJavaIdentifierPart}+");

  private final PsiJavaCodeReferenceElement myRef;

  public AddMavenDependencyQuickFix(PsiJavaCodeReferenceElement ref) {
    myRef = ref;
  }

  @NotNull
  public String getText() {
    return "Add Maven Dependency...";
  }

  @NotNull
  public String getFamilyName() {
    return MavenDomBundle.message("inspection.group");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myRef.isValid() && MavenDomUtil.findContainingProject(file) != null && looksLikeClassName(getReferenceText());
  }

  private static boolean looksLikeClassName(@Nullable String text) {
    if (text == null) return false;
    //if (true) return true;
    return CLASSNAME_PATTERN.matcher(text).matches();
  }

  public void invoke(@NotNull final Project project, Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!myRef.isValid()) return;

    MavenProject mavenProject = MavenDomUtil.findContainingProject(file);
    if (mavenProject == null) return;

    final List<MavenId> ids = MavenArtifactSearchDialog.searchForClass(project, getReferenceText());
    if (ids.isEmpty()) return;

    final MavenDomProjectModel model = MavenDomUtil.getMavenDomProjectModel(project, mavenProject.getFile());
    if (model == null) return;

    WriteCommandAction.writeCommandAction(project, DomUtil.getFile(model)).withName("Add Maven Dependency").run(() -> {
      boolean isTestSource = false;

      VirtualFile virtualFile = file.getOriginalFile().getVirtualFile();
      if (virtualFile != null) {
        isTestSource = ProjectRootManager.getInstance(project).getFileIndex().isInTestSourceContent(virtualFile);
      }

      for (MavenId each : ids) {
        MavenDomDependency dependency = MavenDomUtil.createDomDependency(model, null, each);
        if (isTestSource) {
          dependency.getScope().setStringValue("test");
        }
      }
      ;
    });
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

  public boolean startInWriteAction() {
    return false;
  }
}
