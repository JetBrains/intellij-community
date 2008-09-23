package org.jetbrains.idea.maven.dom.generate;

import com.intellij.openapi.editor.Editor;
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementAction;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.dom.model.Dependency;
import org.jetbrains.idea.maven.dom.model.MavenModel;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchDialog;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class GenerateDependencyAction extends GenerateDomElementAction {
  public GenerateDependencyAction() {
    super(new MavenGenerateProvider<Dependency>("Generate Dependency", Dependency.class) {
      @Override
      protected Dependency doGenerate(MavenModel mavenModel, Editor editor) {
        final MavenId id = MavenArtifactSearchDialog.searchForArtifact(editor.getProject());
        if (id == null) return null;

        MavenProjectsManager manager = MavenProjectsManager.getInstance(editor.getProject());
        return manager.addDependency(manager.findProject(mavenModel.getModule()), id);
      }
    });
  }

  @Override
  protected boolean startInWriteAction() {
    return false;
  }
}
