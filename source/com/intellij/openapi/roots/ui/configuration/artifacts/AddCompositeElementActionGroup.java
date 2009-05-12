package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.packaging.elements.CompositePackagingElementType;
import com.intellij.packaging.elements.PackagingElementFactory;

import java.util.List;

/**
 * @author nik
 */
public class AddCompositeElementActionGroup extends AnAction {
  private final ArtifactsEditor myArtifactsEditor;
  private final CompositePackagingElementType<?> myElementType;

  public AddCompositeElementActionGroup(ArtifactsEditor artifactsEditor, CompositePackagingElementType elementType) {
    super(ProjectBundle.message("artifacts.create.action", elementType.getPresentableName()));
    myArtifactsEditor = artifactsEditor;
    myElementType = elementType;
    getTemplatePresentation().setIcon(elementType.getCreateElementIcon());
  }

  public void actionPerformed(AnActionEvent e) {
    myArtifactsEditor.addNewPackagingElement(myElementType);
  }

  public static void addCompositeCreateActions(List<AnAction> actions, final ArtifactsEditor artifactsEditor) {
    for (CompositePackagingElementType packagingElementType : PackagingElementFactory.getInstance().getCompositeElementTypes()) {
      actions.add(new AddCompositeElementActionGroup(artifactsEditor, packagingElementType));
    }
  }
}