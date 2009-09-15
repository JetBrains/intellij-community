package org.jetbrains.yaml.structureView;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;

/**
 * @author oleg
 */
public class YAMLStructureViewBuilder extends TreeBasedStructureViewBuilder {
  private final YAMLFile myPsiFile;

  public YAMLStructureViewBuilder(@NotNull final YAMLFile psiFile) {
    myPsiFile = psiFile;
  }

  @NotNull
  public StructureViewModel createStructureViewModel() {
    return new StructureViewModelBase(myPsiFile, new YAMLStructureViewElement(myPsiFile))
      .withSorters(Sorter.ALPHA_SORTER)
      .withSuitableClasses(YAMLFile.class, YAMLDocument.class, YAMLKeyValue.class);
  }
}
