package org.jetbrains.idea.maven.dom.generate;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

public class MavenGenerateTemplateAction extends GenerateDomElementAction {
  public MavenGenerateTemplateAction(@NotNull @NlsContexts.DetailedDescription final String description,
                                     @NotNull final Class<? extends DomElement> childElementClass,
                                     @Nullable final String mappingId,
                                     @NotNull Function<? super MavenDomProjectModel, ? extends DomElement> parentFunction) {
    super(new MavenGenerateDomElementProvider(description, childElementClass, mappingId, parentFunction));

    getTemplatePresentation().setIcon(ElementPresentationManager.getIconForClass(childElementClass));
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return file instanceof XmlFile && MavenDomUtil.getMavenDomModel(file, MavenDomProjectModel.class) != null;
  }
}