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
class PackedModuleOutputNode extends ModuleOutputBaseNode {
  private final String myJarFileName;

  PackedModuleOutputNode(@NotNull ModuleLink moduleLink, String jarFileName, PackagingArtifact owner) {
    super(owner, moduleLink);
    myJarFileName = jarFileName;
  }

  @NotNull
  public String getOutputFileName() {
    return myJarFileName;
  }

  public void render(@NotNull final ColoredTreeCellRenderer renderer) {
    Module module = myModuleLink.getModule();
    if (module != null) {
      renderer.append(myJarFileName, getMainAttributes());
      renderer.setIcon(module.getModuleType().getNodeIcon(false));
      renderer.append(getComment(module.getName()), getCommentAttributes());
    }
    else {
      renderer.append(myJarFileName, SimpleTextAttributes.ERROR_ATTRIBUTES);
      renderer.append(getComment(myModuleLink.getName()), getCommentAttributes());
    }
  }

  private static String getComment(final String name) {
    return " " + ProjectBundle.message("node.text.packed.0.compile.output", name);
  }
}
