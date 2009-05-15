package org.jetbrains.idea.maven.dom.generate;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.actions.generate.AbstractDomGenerateProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

public abstract class MavenGenerateProvider<ELEMENT_TYPE extends DomElement> extends AbstractDomGenerateProvider<ELEMENT_TYPE> {
  public MavenGenerateProvider(String description, Class<ELEMENT_TYPE> clazz) {
    super(description, clazz);
  }

  protected DomElement getParentDomElement(Project project, Editor editor, PsiFile file) {
    DomElement el = DomUtil.getContextElement(editor);
    return DomUtil.getFileElement(el).getRootElement();
  }

  @Override
  public ELEMENT_TYPE generate(@Nullable DomElement parent, Editor editor) {
    return doGenerate((MavenDomProjectModel)parent, editor);
  }

  protected abstract ELEMENT_TYPE doGenerate(MavenDomProjectModel mavenModel, Editor editor);

  @Override
  public boolean isAvailableForElement(@NotNull DomElement el) {
    DomElement root = DomUtil.getFileElement(el).getRootElement();
    return root.getModule() != null
           && root instanceof MavenDomProjectModel
           && isAvailableForModel((MavenDomProjectModel)root);
  }

  protected boolean isAvailableForModel(MavenDomProjectModel mavenModel) {
    return true;
  }
}
