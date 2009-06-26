package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.elements.ArtifactRootElementImpl;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public class ArtifactUtil {
  private ArtifactUtil() {
  }

  public static void copyRoot(@NotNull ModifiableArtifact artifact, @Nullable Map<PackagingElement<?>,PackagingElement<?>> old2New) {
    final ArtifactRootElement<?> oldRoot = artifact.getRootElement();
    final ArtifactRootElement newRoot = copyFromRoot(oldRoot, old2New);
    artifact.setRootElement(newRoot);
  }

  public static ArtifactRootElement copyFromRoot(ArtifactRootElement<?> oldRoot, Map<PackagingElement<?>, PackagingElement<?>> old2New) {
    final ArtifactRootElementImpl newRoot = new ArtifactRootElementImpl();
    if (old2New != null) {
      old2New.put(oldRoot, newRoot);
    }
    copyChildren(oldRoot, newRoot, old2New);
    return newRoot;
  }


  private static void copyChildren(CompositePackagingElement<?> oldParent, CompositePackagingElement<?> newParent, 
                                   @Nullable Map<PackagingElement<?>, PackagingElement<?>> old2New) {
    for (PackagingElement<?> child : oldParent.getChildren()) {
      newParent.addOrFindChild(copyWithChildren(child, old2New));
    }
  }

  @NotNull
  public static <S> PackagingElement<S> copyWithChildren(@NotNull PackagingElement<S> element) {
    return copyWithChildren(element, null);
  }

  @NotNull
  public static <S> PackagingElement<S> copyWithChildren(@NotNull PackagingElement<S> element,
                                                         final @Nullable Map<PackagingElement<?>, PackagingElement<?>> old2New) {
    final PackagingElement<S> copy = copyElement(element);
    if (old2New != null) {
      old2New.put(element, copy);
    }
    if (element instanceof CompositePackagingElement<?>) {
      copyChildren((CompositePackagingElement<?>)element, (CompositePackagingElement<?>)copy, old2New);
    }
    return copy;
  }

  @NotNull
  private static <S> PackagingElement<S> copyElement(@NotNull PackagingElement<S> element) {
    final PackagingElement<S> copy = (PackagingElement<S>)element.getType().createEmpty();
    copy.loadState(element.getState());
    return copy;
  }

  public static <E extends PackagingElement<?>> boolean processPackagingElements(@NotNull Artifact artifact, @Nullable PackagingElementType<E> type,
                                                                                 @NotNull Processor<E> processor,
                                                                                 final @NotNull PackagingElementResolvingContext resolvingContext,
                                                                                 final boolean processSubstituions) {
    final List<PackagingElement<?>> children = artifact.getRootElement().getChildren();
    if (!processElements(children, type, processor, resolvingContext, processSubstituions, artifact.getArtifactType())) return false;
    return true;
  }

  private static <E extends PackagingElement<?>> boolean processElements(final List<? extends PackagingElement<?>> elements,
                                                                         @Nullable PackagingElementType<E> type,
                                                                         @NotNull Processor<E> processor,
                                                                         final @NotNull PackagingElementResolvingContext resolvingContext,
                                                                         final boolean processSubstituions, ArtifactType artifactType) {
    for (PackagingElement<?> element : elements) {
      if (!processElements(element, type, processor, resolvingContext, processSubstituions, artifactType)) {
        return false;
      }
    }
    return true;
  }

  private static <E extends PackagingElement<?>> boolean processElements(@NotNull PackagingElement<?> element, @Nullable PackagingElementType<E> type,
                                                                         @NotNull Processor<E> processor,
                                                                         @NotNull PackagingElementResolvingContext resolvingContext,
                                                                         final boolean processSubstituions,
                                                                         ArtifactType artifactType) {
    if (type == null || element.getType().equals(type)) {
      if (!processor.process((E)element)) {
        return false;
      }
    }
    if (element instanceof CompositePackagingElement<?>) {
      return processElements(((CompositePackagingElement<?>)element).getChildren(), type, processor, resolvingContext, processSubstituions,
                             artifactType);
    }
    else if (element instanceof ComplexPackagingElement<?> && processSubstituions) {
      final List<? extends PackagingElement<?>> substitution = ((ComplexPackagingElement<?>)element).getSubstitution(resolvingContext,
                                                                                                                     artifactType);
      if (substitution != null) {
        return processElements(substitution, type, processor, resolvingContext, processSubstituions, artifactType);
      }
    }
    return true;
  }

  public static void removeDuplicates(@NotNull CompositePackagingElement<?> parent) {
    List<PackagingElement<?>> prevChildren = new ArrayList<PackagingElement<?>>();

    List<PackagingElement<?>> toRemove = new ArrayList<PackagingElement<?>>();
    for (PackagingElement<?> child : parent.getChildren()) {
      if (child instanceof CompositePackagingElement<?>) {
        removeDuplicates((CompositePackagingElement<?>)child);
      }
      boolean merged = false;
      for (PackagingElement<?> prevChild : prevChildren) {
        if (child.isEqualTo(prevChild)) {
          if (child instanceof CompositePackagingElement<?>) {
            for (PackagingElement<?> childElement : ((CompositePackagingElement<?>)child).getChildren()) {
              ((CompositePackagingElement<?>)prevChild).addOrFindChild(childElement);
            }
          }
          merged = true;
          break;
        }
      }
      if (merged) {
        toRemove.add(child);
      }
      else {
        prevChildren.add(child);
      }
    }

    for (PackagingElement<?> child : toRemove) {
      parent.removeChild(child);
    }
  }

  public static <S> void copyProperties(ArtifactProperties<?> from, ArtifactProperties<S> to) {
    to.loadState((S)from.getState());
  }
}
