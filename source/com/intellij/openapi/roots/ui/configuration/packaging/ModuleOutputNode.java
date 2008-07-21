package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.deployment.ModuleLink;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
class ModuleOutputNode extends ModuleOutputBaseNode {

  ModuleOutputNode(final ModuleLink moduleLink, final PackagingArtifact owner) {
    super(owner, moduleLink);
  }

  @NotNull
  protected String getOutputFileName() {
    return myModuleLink.getName();
  }

  public void render(final ColoredTreeCellRenderer renderer) {
    final Module module = myModuleLink.getModule();
    if (module == null) {
      renderer.append(myModuleLink.getName(), SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
    else {
      renderer.append(module.getName(), getMainAttributes());
      renderer.setIcon(module.getModuleType().getNodeIcon(false));
    }
    renderer.append(" " + ProjectBundle.message("node.text.packaging.compile.output"), getCommentAttributes());
    super.render(renderer);
  }

}
