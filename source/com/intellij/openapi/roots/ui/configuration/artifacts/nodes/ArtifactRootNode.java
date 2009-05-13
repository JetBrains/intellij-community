package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsEditor;
import com.intellij.openapi.roots.ui.configuration.artifacts.ComplexElementSubstitutionParameters;
import com.intellij.packaging.ui.PackagingEditorContext;

import java.util.Collections;

/**
 * @author nik
 */
public class ArtifactRootNode extends CompositePackagingElementNode {
  public ArtifactRootNode(ArtifactsEditor artifactEditor, PackagingEditorContext context,
                          ComplexElementSubstitutionParameters substitutionParameters) {
    super(artifactEditor.getRootElement(), context, null, null, substitutionParameters, Collections.<PackagingNodeSource>emptyList());
  }

  @Override
  public boolean isAutoExpandNode() {
    return true;
  }
}
