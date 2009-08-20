package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.openapi.module.Module;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFilesKind;
import com.intellij.packaging.impl.elements.ModuleOutputPackagingElement;
import com.intellij.packaging.impl.ui.ModuleElementPresentation;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.SourceItemPresentation;
import com.intellij.packaging.ui.SourceItemWeights;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ModuleOutputSourceItem extends PackagingSourceItem {
  private final Module myModule;

  public ModuleOutputSourceItem(@NotNull Module module) {
    myModule = module;
  }

  public boolean equals(Object obj) {
    return obj instanceof ModuleOutputSourceItem && myModule.equals(((ModuleOutputSourceItem)obj).myModule);
  }

  public int hashCode() {
    return myModule.hashCode();
  }

  @Override
  public SourceItemPresentation createPresentation(@NotNull PackagingEditorContext context) {
    return new DelegatedSourceItemPresentation(new ModuleElementPresentation(myModule.getName(), myModule)) {
      @Override
      public int getWeight() {
        return SourceItemWeights.MODULE_OUTPUT_WEIGHT;
      }
    };
  }

  @NotNull
  public List<? extends PackagingElement<?>> createElements(@NotNull PackagingEditorContext context) {
    return Collections.singletonList(new ModuleOutputPackagingElement(myModule.getName()));
  }

  @NotNull
  @Override
  public PackagingElementFilesKind getKindOfProducedElements() {
    return PackagingElementFilesKind.DIRECTORIES_WITH_CLASSES;
  }
}
