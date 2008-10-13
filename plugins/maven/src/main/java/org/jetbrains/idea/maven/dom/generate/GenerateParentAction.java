package org.jetbrains.idea.maven.dom.generate;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementAction;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenModel;
import org.jetbrains.idea.maven.dom.model.MavenParent;
import org.jetbrains.idea.maven.navigator.SelectMavenProjectDialog;
import org.jetbrains.idea.maven.project.MavenProjectModel;

public class GenerateParentAction extends GenerateDomElementAction {
  public GenerateParentAction() {
    super(new MavenGenerateProvider<MavenParent>("Generate Parent", MavenParent.class) {
      protected MavenParent doGenerate(final MavenModel mavenModel, Editor editor) {
        SelectMavenProjectDialog d = new SelectMavenProjectDialog(editor.getProject(), null);
        d.show();
        if (!d.isOK()) return null;
        final MavenProjectModel parentProject = d.getResult();
        if (parentProject == null) return null;

        return new WriteCommandAction<MavenParent>(editor.getProject(), getDescription()) {
          protected void run(Result result) throws Throwable {
            result.setResult(MavenDomUtil.updateMavenParent(mavenModel, parentProject));
          }
        }.execute().getResultObject();
      }

      @Override
      protected boolean isAvailableForModel(MavenModel mavenModel) {
        return mavenModel.getMavenParent().getXmlElement() == null;
      }
    });
  }

  @Override
  protected boolean startInWriteAction() {
    return false;
  }
}
