package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactProcessorProvider;
import com.intellij.packaging.artifacts.ArtifactProcessor;
import com.intellij.packaging.ui.PackagingEditorContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class ArtifactPostprocessingPanel {
  private JPanel myMainPanel;
  private final PackagingEditorContext myContext;

  public ArtifactPostprocessingPanel(PackagingEditorContext context) {
    myContext = context;
    myMainPanel = new JPanel(new VerticalFlowLayout());
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public void updateProcessors(@NotNull Artifact artifact) {
    final ArtifactProcessorProvider[] providers = Extensions.getExtensions(ArtifactProcessorProvider.EP_NAME);
    for (ArtifactProcessorProvider provider : providers) {
      final ArtifactProcessor processor = provider.createProcessor(artifact, myContext);
      if (processor != null) {
        myMainPanel.add(processor.createConfigurable().createComponent());
      }
    }
  }
}
