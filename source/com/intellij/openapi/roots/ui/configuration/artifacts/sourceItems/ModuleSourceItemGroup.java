package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.openapi.module.Module;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.SourceItemPresentation;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.SourceItemWeights;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.elements.ModuleWithDependenciesPackagingElement;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ide.projectView.PresentationData;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Collections;

/**
 * @author nik
 */
public class ModuleSourceItemGroup extends PackagingSourceItem {
  private final Module myModule;

  public ModuleSourceItemGroup(Module module) {
    myModule = module;
  }

  public SourceItemPresentation createPresentation(@NotNull PackagingEditorContext context) {
    return new ModuleSourceItemPresentation(myModule);
  }

  public boolean equals(Object obj) {
    return obj instanceof ModuleSourceItemGroup && myModule.equals(((ModuleSourceItemGroup)obj).myModule);
  }

  public int hashCode() {
    return myModule.hashCode();
  }

  @NotNull
  public List<? extends PackagingElement<?>> createElements(@NotNull PackagingEditorContext context) {
    return Collections.singletonList(new ModuleWithDependenciesPackagingElement(myModule.getName()));
  }

  public Module getModule() {
    return myModule;
  }

  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    renderer.append(myModule.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    renderer.setIcon(myModule.getModuleType().getNodeIcon(false));
  }

  private static class ModuleSourceItemPresentation extends SourceItemPresentation {
    private final Module myModule;

    public ModuleSourceItemPresentation(Module module) {
      myModule = module;
    }

    @Override
    public String getPresentableName() {
      return myModule.getName();
    }

    @Override
    public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes,
                       SimpleTextAttributes commentAttributes) {
      presentationData.setClosedIcon(myModule.getModuleType().getNodeIcon(false));
      presentationData.setOpenIcon(myModule.getModuleType().getNodeIcon(true));
      presentationData.addText(myModule.getName(), mainAttributes);
    }

    @Override
    public int getWeight() {
      return SourceItemWeights.MODULE_WEIGHT;
    }
  }
}
