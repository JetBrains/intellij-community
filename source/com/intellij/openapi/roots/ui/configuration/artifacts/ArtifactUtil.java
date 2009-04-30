package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.elements.ArtifactRootElementImpl;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactUtil {
  private ArtifactUtil() {
  }

  public static void copyRoot(@NotNull ModifiableArtifact artifact, @Nullable Map<PackagingElement<?>,PackagingElement<?>> old2New) {
    final ArtifactRootElement<?> oldRoot = artifact.getRootElement();
    final ArtifactRootElementImpl newRoot = new ArtifactRootElementImpl();
    artifact.setRootElement(newRoot);
    if (old2New != null) {
      old2New.put(oldRoot, newRoot);
    }
    copyChildren(oldRoot, newRoot, old2New);
  }


  private static void copyChildren(CompositePackagingElement<?> oldParent, CompositePackagingElement<?> newParent, 
                                   @Nullable Map<PackagingElement<?>, PackagingElement<?>> old2New) {
    for (PackagingElement<?> child : oldParent.getChildren()) {
      newParent.addChild(copyWithChildren(child, old2New));
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

  public static <E extends PackagingElement<?>> boolean processPackagingElements(@NotNull Artifact artifact, @NotNull PackagingElementType<E> type,
                                                                                 @NotNull Processor<E> processor,
                                                                                 final @NotNull PackagingElementResolvingContext resolvingContext,
                                                                                 final boolean processSubstituions) {
    if (!processElements(artifact.getRootElement().getChildren(), type, processor, resolvingContext, processSubstituions)) return false;
    return true;
  }

  private static <E extends PackagingElement<?>> boolean processElements(final List<? extends PackagingElement<?>> elements,
                                                                         PackagingElementType<E> type,
                                                                         @NotNull Processor<E> processor,
                                                                         final @NotNull PackagingElementResolvingContext resolvingContext,
                                                                         final boolean processSubstituions) {
    for (PackagingElement<?> element : elements) {
      if (!processElements(element, type, processor, resolvingContext, processSubstituions)) {
        return false;
      }
    }
    return true;
  }

  private static <E extends PackagingElement<?>> boolean processElements(@NotNull PackagingElement<?> element, @NotNull PackagingElementType<E> type,
                                                                         @NotNull Processor<E> processor,
                                                                         @NotNull PackagingElementResolvingContext resolvingContext,
                                                                         final boolean processSubstituions) {
    if (element.getType().equals(type)) {
      if (!processor.process((E)element)) {
        return false;
      }
    }
    if (element instanceof CompositePackagingElement<?>) {
      return processElements(((CompositePackagingElement<?>)element).getChildren(), type, processor, resolvingContext, processSubstituions);
    }
    else if (element instanceof ComplexPackagingElement<?> && processSubstituions) {
      final List<? extends PackagingElement<?>> substitution = ((ComplexPackagingElement<?>)element).getSubstitution(resolvingContext);
      return processElements(substitution, type, processor, resolvingContext, processSubstituions);
    }
    return true;
  }
}
