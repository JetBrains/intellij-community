package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactUtil;
import com.intellij.openapi.roots.ui.configuration.artifacts.ManifestFilesInfo;
import com.intellij.openapi.roots.ui.configuration.artifacts.PackagingElementProcessor;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.elements.ArtifactElementType;
import com.intellij.packaging.impl.elements.ArtifactPackagingElement;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class ArtifactExternalDependenciesImporter {
  private ManifestFilesInfo myManifestFiles = new ManifestFilesInfo();
  private Map<Artifact, List<PackagingElement<?>>> myExternalDependencies = new HashMap<Artifact, List<PackagingElement<?>>>();

  public ManifestFileConfiguration getManifestFile(@NotNull Artifact artifact,
                                                   @NotNull PackagingElementResolvingContext context) {
    return myManifestFiles.getManifestFile(artifact.getRootElement(), artifact.getArtifactType(), context);
  }

  public List<PackagingElement<?>> getExternalDependenciesList(@NotNull Artifact artifact) {
    List<PackagingElement<?>> elements = myExternalDependencies.get(artifact);
    if (elements == null) {
      elements = new ArrayList<PackagingElement<?>>();
      myExternalDependencies.put(artifact, elements);
    }
    return elements;
  }

  public void applyChanges(ModifiableArtifactModel artifactModel, final PackagingElementResolvingContext context) {
    myManifestFiles.saveManifestFiles();
    for (Artifact artifact : artifactModel.getArtifacts()) {
      ArtifactUtil.processPackagingElements(artifact, ArtifactElementType.ARTIFACT_ELEMENT_TYPE, new PackagingElementProcessor<ArtifactPackagingElement>() {
        @Override
        public boolean process(@NotNull List<CompositePackagingElement<?>> parents,
                               @NotNull ArtifactPackagingElement artifactPackagingElement) {
          final Artifact included = artifactPackagingElement.findArtifact(context);
          if (!parents.isEmpty() && included != null) {
            final CompositePackagingElement<?> parent = parents.get(0);
            final List<PackagingElement<?>> elements = myExternalDependencies.get(included);
            if (elements != null) {
              parent.addOrFindChildren(elements);
            }
          }
          return true;
        }
      }, context, false);
    }
  }
}
