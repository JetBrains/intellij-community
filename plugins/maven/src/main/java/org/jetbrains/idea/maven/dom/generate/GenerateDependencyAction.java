package org.jetbrains.idea.maven.dom.generate;

import com.intellij.openapi.editor.Editor;
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementAction;
import org.jetbrains.idea.maven.project.MavenId;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchDialog;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class GenerateDependencyAction extends GenerateDomElementAction {
  public GenerateDependencyAction() {
    super(new MavenGenerateProvider<MavenDomDependency>("Generate Dependency", MavenDomDependency.class) {
      @Override
      protected MavenDomDependency doGenerate(MavenDomProjectModel mavenModel, Editor editor) {
        MavenId id = MavenArtifactSearchDialog.searchForArtifact(editor.getProject());
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
