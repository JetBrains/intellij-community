package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.openapi.roots.ui.configuration.artifacts.ComplexElementSubstitutionParameters;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsEditorImpl;
import com.intellij.packaging.elements.ArtifactRootElement;
import com.intellij.packaging.elements.ComplexPackagingElement;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.PackagingEditorContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class PackagingTreeNodeFactory {
  private PackagingTreeNodeFactory() {
  }

  public static List<? extends PackagingElementNode<?>> createNodes(@NotNull List<? extends PackagingElement<?>> elements, final PackagingElementNode<?> parent,
                                                   @NotNull PackagingEditorContext context,
                                                   @NotNull ComplexElementSubstitutionParameters substitutionParameters) {
    List<PackagingElementNode<?>> nodes = new ArrayList<PackagingElementNode<?>>();

    addNodes(elements, parent, context, substitutionParameters, nodes);

    return nodes;
  }

  private static void addNodes(List<? extends PackagingElement<?>> elements, PackagingElementNode<?> parent, PackagingEditorContext context,
                               ComplexElementSubstitutionParameters substitutionParameters, List<PackagingElementNode<?>> nodes) {
    for (PackagingElement<?> element : elements) {
      final PackagingElementNode<?> prev = findEqual(nodes, element);
      if (prev != null) {
        prev.addElement(element);
        continue;
      }

      if (element instanceof ArtifactRootElement) {
        throw new AssertionError("artifact root not expected here");
      }
      else if (element instanceof CompositePackagingElement) {
        nodes.add(new CompositePackagingElementNode((CompositePackagingElement<?>)element, context, parent, substitutionParameters));
      }
      else if (element instanceof ComplexPackagingElement) {
        final ComplexPackagingElement<?> complexElement = (ComplexPackagingElement<?>)element;
        if (substitutionParameters.shouldSubstitute(complexElement)) {
          final List<? extends PackagingElement<?>> substitution = complexElement.getSubstitution(context);
          if (substitution != null) {
            addNodes(substitution, parent, context, substitutionParameters, nodes);
            continue;
          }
        }
        nodes.add(new ComplexPackagingElementNode(complexElement, context, parent, substitutionParameters));
      }
      else {
        nodes.add(new PackagingElementNode<PackagingElement<?>>(element, context, parent));
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
  public static ArtifactRootNode createRootNode(ArtifactsEditorImpl artifactsEditor, PackagingEditorContext context,
                                      ComplexElementSubstitutionParameters substitutionParameters) {
    return new ArtifactRootNode(artifactsEditor, context, substitutionParameters);
  }

  public static Object createRootNode() {
    return null;
  }
}
