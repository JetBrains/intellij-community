// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.idea.maven.utils.MavenLog;

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
  public @NotNull String getText() {
    return MavenDomBundle.message("fix.add.dependency");
  }

  @Override
  public @NotNull String getFamilyName() {
    return MavenDomBundle.message("inspection.group");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return myRef.isValid() && MavenDomUtil.findContainingProject(psiFile) != null && looksLikeClassName(getReferenceText());
  }

  private static boolean looksLikeClassName(@Nullable String text) {
    if (text == null) return false;
    //if (true) return true;
    return CLASSNAME_PATTERN.matcher(text).matches();
  }

  @Override
  public void invoke(final @NotNull Project project, Editor editor, final PsiFile psiFile) throws IncorrectOperationException {
    if (!myRef.isValid()) return;

    MavenProject mavenProject = MavenDomUtil.findContainingProject(psiFile);
    if (mavenProject == null) return;

    final List<MavenId> ids = MavenArtifactSearchDialog.searchForClass(project, getReferenceText());
    if (ids.isEmpty()) return;

    final MavenDomProjectModel model = MavenDomUtil.getMavenDomProjectModel(project, mavenProject.getFile());
    if (model == null) return;

    WriteCommandAction.writeCommandAction(project, DomUtil.getFile(model)).withName(
      MavenDomBundle.message("maven.dom.quickfix.add.maven.dependency")).run(() -> {
      boolean isTestSource = false;

      VirtualFile virtualFile = psiFile.getOriginalFile().getVirtualFile();
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
    MavenLog.LOG.info("AddMavenDependencyQuickFix forceUpdateAllProjectsOrFindAllAvailablePomFiles");
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
