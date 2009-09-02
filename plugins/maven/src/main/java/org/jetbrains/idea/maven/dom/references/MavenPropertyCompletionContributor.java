package org.jetbrains.idea.maven.dom.references;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.DummyIdentifierPatcher;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class MavenPropertyCompletionContributor extends CompletionContributor {
  @Override
  public void beforeCompletion(@NotNull CompletionInitializationContext context) {
    Project project = context.getProject();
    PsiFile psiFile = context.getFile();

    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    if (!manager.isMavenizedProject()) return;

    MavenProject projectFile = MavenDomUtil.findContainingProject(psiFile);
    if (projectFile == null) return;

    MavenDomProjectModel model = MavenDomUtil.getMavenDomProjectModel(project, projectFile.getFile());
    if (model == null && !MavenDomUtil.isFiltererResourceFile(psiFile)) return;

    CharSequence text = context.getEditor().getDocument().getCharsSequence();
    int offset = context.getStartOffset();
    if (offset >= 2 && text.charAt(offset - 2) == '$' && text.charAt(offset - 1) == '{') {
      context.setFileCopyPatcher(new DummyIdentifierPatcher(CompletionInitializationContext.DUMMY_IDENTIFIER.trim() + "}"));
    }
  }
}
