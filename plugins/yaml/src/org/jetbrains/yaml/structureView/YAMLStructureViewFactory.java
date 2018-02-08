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
    for (YAMLCustomStructureViewFactory extension : YAMLCustomStructureViewFactory.EP_NAME.getExtensions()) {
      final StructureViewBuilder builder = extension.getStructureViewBuilder((YAMLFile)psiFile);
      if (builder != null) {
        return builder;
      }
    }

    return new YAMLStructureViewBuilder((YAMLFile) psiFile);
  }
}
