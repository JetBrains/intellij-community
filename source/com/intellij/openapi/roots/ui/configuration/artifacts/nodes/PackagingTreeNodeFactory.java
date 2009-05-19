package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorImpl;
import com.intellij.openapi.roots.ui.configuration.artifacts.ComplexElementSubstitutionParameters;
import com.intellij.packaging.elements.ArtifactRootElement;
import com.intellij.packaging.elements.ComplexPackagingElement;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.artifacts.ArtifactType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class PackagingTreeNodeFactory {
  private PackagingTreeNodeFactory() {
  }

  public static List<? extends PackagingElementNode<?>> createNodes(@NotNull List<? extends PackagingElement<?>> elements,
                                                                    final @NotNull CompositePackagingElementNode parentNode,
                                                                    final @NotNull CompositePackagingElement parentElement,
                                                                    @NotNull PackagingEditorContext context,
                                                                    @NotNull ComplexElementSubstitutionParameters substitutionParameters,
                                                                    @NotNull Collection<PackagingNodeSource> nodeSources,
                                                                    final ArtifactType artifactType) {
    List<PackagingElementNode<?>> nodes = new ArrayList<PackagingElementNode<?>>();

    addNodes(elements, parentNode, parentElement, context, substitutionParameters, nodeSources, nodes, artifactType);

    return nodes;
  }

  private static void addNodes(@NotNull List<? extends PackagingElement<?>> elements, @NotNull CompositePackagingElementNode parentNode,
                               @NotNull CompositePackagingElement parentElement, @NotNull PackagingEditorContext context,
                               @NotNull ComplexElementSubstitutionParameters substitutionParameters, @NotNull Collection<PackagingNodeSource> nodeSources,
                               @NotNull List<PackagingElementNode<?>> nodes,
                               ArtifactType artifactType) {
    for (PackagingElement<?> element : elements) {
      final PackagingElementNode<?> prev = findEqual(nodes, element);
      if (prev != null) {
        prev.addElement(element, parentElement, nodeSources);
        continue;
      }

      if (element instanceof ArtifactRootElement) {
        throw new AssertionError("artifact root not expected here");
      }
      else if (element instanceof CompositePackagingElement) {
        nodes.add(new CompositePackagingElementNode((CompositePackagingElement<?>)element, context, parentNode, parentElement, substitutionParameters, nodeSources,
                                                    artifactType));
      }
      else if (element instanceof ComplexPackagingElement) {
        final ComplexPackagingElement<?> complexElement = (ComplexPackagingElement<?>)element;
        if (substitutionParameters.shouldSubstitute(complexElement)) {
          final List<? extends PackagingElement<?>> substitution = complexElement.getSubstitution(context, artifactType);
          if (substitution != null) {
            final PackagingNodeSource source = new PackagingNodeSource(complexElement, parentNode, parentElement, nodeSources);
            addNodes(substitution, parentNode, parentElement, context, substitutionParameters, Collections.singletonList(source), nodes,
                     artifactType);
            continue;
          }
        }
        nodes.add(new ComplexPackagingElementNode(complexElement, context, parentNode, parentElement, substitutionParameters, nodeSources));
      }
      else {
        nodes.add(new PackagingElementNode<PackagingElement<?>>(element, context, parentNode, parentElement, nodeSources));
      }
    }
  }

  @Nullable
  private static PackagingElementNode<?> findEqual(List<PackagingElementNode<?>> children, PackagingElement<?> element) {
    for (PackagingElementNode<?> node : children) {
      if (node.getFirstElement().isEqualTo(element)) {
        return node;
      }
    }
    return null;
  }

  @NotNull
  public static ArtifactRootNode createRootNode(ArtifactEditorImpl artifactsEditor, PackagingEditorContext context,
                                                ComplexElementSubstitutionParameters substitutionParameters, final ArtifactType artifactType) {
    return new ArtifactRootNode(artifactsEditor, context, substitutionParameters, artifactType);
  }
}
