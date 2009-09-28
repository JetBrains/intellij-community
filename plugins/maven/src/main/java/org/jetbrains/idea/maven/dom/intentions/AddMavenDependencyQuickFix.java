package org.jetbrains.idea.maven.dom.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchDialog;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenId;

public class AddMavenDependencyQuickFix implements IntentionAction {
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
    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    if (!manager.isMavenizedProject()) return false;

    Module module = getModuleForFile(file);
    return module != null && manager.isMavenizedModule(module);
  }

  private Module getModuleForFile(PsiFile file) {
    ProjectFileIndex index = ProjectRootManager.getInstance(file.getProject()).getFileIndex();
    return index.getModuleForFile(file.getVirtualFile());
  }

  public void invoke(@NotNull final Project project, Editor editor, final PsiFile file) throws IncorrectOperationException {
    MavenId dependency = MavenArtifactSearchDialog.searchForClass(project, getReferenceText());
    if (dependency == null) return;

    Module module = getModuleForFile(file);
    MavenProject mavenProject = MavenProjectsManager.getInstance(project).findProject(module);

    MavenProjectsManager.getInstance(project).addDependency(mavenProject, dependency);
  }

  private String getReferenceText() {
    PsiElement result = myRef;
    while(result.getParent() instanceof PsiJavaCodeReferenceElement) {
      result = result.getParent();
    }
    return result.getText();
  }

  public boolean startInWriteAction() {
    return false;
  }
}
