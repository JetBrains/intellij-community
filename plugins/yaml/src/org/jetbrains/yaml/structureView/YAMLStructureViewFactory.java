package org.jetbrains.yaml.structureView;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLFile;

/**
 * @author oleg
 */
public class YAMLStructureViewFactory implements PsiStructureViewFactory {
  public StructureViewBuilder getStructureViewBuilder(@NotNull final PsiFile psiFile) {
    return new YAMLStructureViewBuilder((YAMLFile) psiFile);
  }
}
