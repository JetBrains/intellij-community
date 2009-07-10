package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.ComplexElementSubstitutionParameters;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.artifacts.ArtifactType;

import java.util.Collections;

/**
 * @author nik
 */
public class ArtifactRootNode extends CompositePackagingElementNode {
  public ArtifactRootNode(ArtifactEditorEx artifactEditor, PackagingEditorContext context,
                          ComplexElementSubstitutionParameters substitutionParameters, ArtifactType artifactType) {
    super(artifactEditor.getRootElement(), context, null, null, substitutionParameters, Collections.<PackagingNodeSource>emptyList(),
          artifactType);
  }

  @Override
  public boolean isAutoExpandNode() {
    return true;
  }
}
