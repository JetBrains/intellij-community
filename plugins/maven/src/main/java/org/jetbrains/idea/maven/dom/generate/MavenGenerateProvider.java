package org.jetbrains.idea.maven.dom.generate;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.actions.generate.AbstractDomGenerateProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenModel;

public abstract class MavenGenerateProvider<ELEMENT_TYPE extends DomElement> extends AbstractDomGenerateProvider<ELEMENT_TYPE> {
  public MavenGenerateProvider(String description, Class<ELEMENT_TYPE> clazz) {
    super(description, clazz);
  }

  protected DomElement getParentDomElement(Project project, Editor editor, PsiFile file) {
    DomElement el = DomUtil.getContextElement(editor);
    return el.getRoot().getRootElement();
  }

  @Override
  public ELEMENT_TYPE generate(@Nullable DomElement parent, Editor editor) {
    return doGenerate((MavenModel)parent, editor);
  }

  protected abstract ELEMENT_TYPE doGenerate(MavenModel mavenModel, Editor editor);

  @Override
  public boolean isAvailableForElement(@NotNull DomElement el) {
    DomElement root = el.getRoot().getRootElement();
    return root.getModule() != null
           && root instanceof MavenModel
           && isAvailableForModel((MavenModel)root);
  }

  protected boolean isAvailableForModel(MavenModel mavenModel) {
    return true;
  }
}
