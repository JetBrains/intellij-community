package org.jetbrains.idea.maven.dom.generate;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.util.xml.actions.generate.DomTemplateRunner;
import com.intellij.util.xml.impl.DomTemplateRunnerImpl;
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementAction;
import org.jetbrains.idea.maven.dom.model.MavenModel;
import org.jetbrains.idea.maven.dom.model.MavenParent;

public class GenerateParentAction extends GenerateDomElementAction {
  public GenerateParentAction() {
    super(new MavenGenerateProvider<MavenParent>("Generate Parent", MavenParent.class) {
      protected MavenParent doGenerate(MavenModel mavenModel, Editor editor) {
        MavenParent mavenParent = mavenModel.getMavenParent();
        mavenParent.ensureTagExists();
        return mavenParent;
      }

      @Override
      protected void runTemplate(Editor editor, PsiFile file, MavenParent mavenParent) {
        TemplateManager manager = TemplateManager.getInstance(file.getProject());
        Template template = manager.createTemplate("", "");

        template.addTextSegment("<parent>");
        template.addTextSegment("<groupId>");
        template.addVariable("groupId", new ConstantNode("groupId"), new ConstantNode("groupId"), true);
        template.addTextSegment("</groupId>");
        template.addTextSegment("<artifactId>");
        template.addVariable("artifactId", new ConstantNode("artifactId"), new ConstantNode("artifactId"), true);
        template.addTextSegment("</artifactId>");
        template.addTextSegment("<version>");
        template.addVariable("version", new ConstantNode("version"), new ConstantNode("artifactversion"), true);
        template.addTextSegment("</version>");
        template.addTextSegment("</parent>");

        ((DomTemplateRunnerImpl)DomTemplateRunner.getInstance(file.getProject())).runTemplate(mavenParent, editor, template);
      }

      @Override
      protected boolean isAvailableForModel(MavenModel mavenModel) {
        return mavenModel.getMavenParent().getXmlElement() == null;
      }
    });
  }
}
