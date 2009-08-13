package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.elements.FileCopyPackagingElement;
import com.intellij.packaging.impl.elements.DirectoryPackagingElement;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactUtil {
  private ArtifactUtil() {
  }

  public static CompositePackagingElement<?> copyFromRoot(@NotNull CompositePackagingElement<?> oldRoot, ArtifactType artifactType) {
    final CompositePackagingElement<?> newRoot = (CompositePackagingElement<?>)copyElement(oldRoot);
    copyChildren(oldRoot, newRoot);
    return newRoot;
  }


  public static void copyChildren(CompositePackagingElement<?> oldParent, CompositePackagingElement<?> newParent) {
    for (PackagingElement<?> child : oldParent.getChildren()) {
      newParent.addOrFindChild(copyWithChildren(child));
    }
  }

  @NotNull
  public static <S> PackagingElement<S> copyWithChildren(@NotNull PackagingElement<S> element) {
    final PackagingElement<S> copy = copyElement(element);
    if (element instanceof CompositePackagingElement<?>) {
      copyChildren((CompositePackagingElement<?>)element, (CompositePackagingElement<?>)copy);
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
                                                                                 @NotNull final Processor<E> processor,
                                                                                 final @NotNull PackagingElementResolvingContext resolvingContext,
                                                                                 final boolean processSubstituions) {
    return processPackagingElements(artifact, type, new PackagingElementProcessor<E>() {
      @Override
      public boolean process(@NotNull List<CompositePackagingElement<?>> parents, @NotNull E e) {
        return processor.process(e);
      }
    }, resolvingContext, processSubstituions);
  }

  public static <E extends PackagingElement<?>> boolean processPackagingElements(@NotNull Artifact artifact, @Nullable PackagingElementType<E> type,
                                                                                 @NotNull PackagingElementProcessor<E> processor,
                                                                                 final @NotNull PackagingElementResolvingContext resolvingContext,
                                                                                 final boolean processSubstituions) {
    return processPackagingElements(artifact.getRootElement(), type, processor, resolvingContext, processSubstituions, artifact.getArtifactType());
  }

  public static <E extends PackagingElement<?>> boolean processPackagingElements(final CompositePackagingElement<?> rootElement, @Nullable PackagingElementType<E> type,
                                                                                 @NotNull PackagingElementProcessor<E> processor,
                                                                                 final @NotNull PackagingElementResolvingContext resolvingContext,
                                                                                 final boolean processSubstituions,
                                                                                 final ArtifactType artifactType) {
    return processElements(rootElement, type, processor, resolvingContext, processSubstituions, artifactType,
                           FList.<CompositePackagingElement<?>>emptyList());
  }

  private static <E extends PackagingElement<?>> boolean processElements(final List<? extends PackagingElement<?>> elements,
                                                                         @Nullable PackagingElementType<E> type,
                                                                         @NotNull PackagingElementProcessor<E> processor,
                                                                         final @NotNull PackagingElementResolvingContext resolvingContext,
                                                                         final boolean processSubstituions, ArtifactType artifactType,
                                                                         FList<CompositePackagingElement<?>> parents) {
    for (PackagingElement<?> element : elements) {
      if (!processElements(element, type, processor, resolvingContext, processSubstituions, artifactType, parents)) {
        return false;
      }
    }
    return true;
  }

  private static <E extends PackagingElement<?>> boolean processElements(@NotNull PackagingElement<?> element, @Nullable PackagingElementType<E> type,
                                                                         @NotNull PackagingElementProcessor<E> processor,
                                                                         @NotNull PackagingElementResolvingContext resolvingContext,
                                                                         final boolean processSubstituions,
                                                                         ArtifactType artifactType,
                                                                         FList<CompositePackagingElement<?>> parents) {
    if (type == null || element.getType().equals(type)) {
      if (!processor.process(parents, (E)element)) {
        return false;
      }
    }
    if (element instanceof CompositePackagingElement<?>) {
      final CompositePackagingElement<?> composite = (CompositePackagingElement<?>)element;
      return processElements(composite.getChildren(), type, processor, resolvingContext, processSubstituions,
                             artifactType, parents.prepend(composite));
    }
    else if (element instanceof ComplexPackagingElement<?> && processSubstituions) {
      final ComplexPackagingElement<?> complexElement = (ComplexPackagingElement<?>)element;
      if (processor.shouldProcessSubstitution(complexElement)) {
        final List<? extends PackagingElement<?>> substitution = complexElement.getSubstitution(resolvingContext,
                                                                                                artifactType);
        if (substitution != null) {
          return processElements(substitution, type, processor, resolvingContext, processSubstituions, artifactType, parents);
        }
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

  @Nullable
  public static String getDefaultArtifactOutputPath(@NotNull String artifactName, final @NotNull Project project) {
    final String outputUrl = CompilerProjectExtension.getInstance(project).getCompilerOutputUrl();
    return outputUrl != null ? VfsUtil.urlToPath(outputUrl) + "/artifacts/" + FileUtil.sanitizeFileName(artifactName) : null;
  }

  public static boolean processElements(@NotNull List<? extends PackagingElement<?>> elements,
                                        @NotNull PackagingElementResolvingContext context,
                                        @NotNull ArtifactType artifactType,
                                        @NotNull Processor<PackagingElement<?>> processor) {
    for (PackagingElement<?> element : elements) {
      if (element instanceof ComplexPackagingElement<?>) {
        final List<? extends PackagingElement<?>> substitution =
            ((ComplexPackagingElement<?>)element).getSubstitution(context, artifactType);
        if (substitution != null && !processElements(substitution, context, artifactType, processor)) {
          return false;
        }
      }
      else if (!processor.process(element)) {
        return false;
      }
    }
    return true;
  }

  public static List<PackagingElement<?>> findByRelativePath(@NotNull CompositePackagingElement<?> parent, @NotNull String relativePath,
                                                       @NotNull PackagingElementResolvingContext context, @NotNull ArtifactType artifactType) {
    relativePath = StringUtil.trimStart(relativePath, "/");
    if (relativePath.length() == 0) {
      return new SmartList<PackagingElement<?>>(parent);
    }

    int i = relativePath.indexOf('/');
    final String firstName = i != -1 ? relativePath.substring(0, i) : relativePath;
    String tail = i != -1 ? relativePath.substring(i+1) : "";

    final List<PackagingElement<?>> children = new SmartList<PackagingElement<?>>();
    processElements(parent.getChildren(), context, artifactType, new Processor<PackagingElement<?>>() {
      public boolean process(PackagingElement<?> element) {
        if (element instanceof CompositePackagingElement && firstName.equals(((CompositePackagingElement<?>)element).getName())) {
          children.add(element);
        }
        else if (element instanceof FileCopyPackagingElement) {
          final FileCopyPackagingElement fileCopy = (FileCopyPackagingElement)element;
          if (!fileCopy.isDirectory() && firstName.equals(fileCopy.getFileName())) {
            children.add(element);
          }
        }
        return true;
      }
    });

    if (tail.length() == 0) {
      return children;
    }

    List<PackagingElement<?>> result = new SmartList<PackagingElement<?>>();
    for (PackagingElement<?> child : children) {
      if (child instanceof CompositePackagingElement) {
        result.addAll(findByRelativePath((CompositePackagingElement<?>)child, tail, context, artifactType));
      }
    }
    return result;
  }

  public static boolean processDirectoryChildren(@NotNull CompositePackagingElement<?> root,
                                                 @NotNull String relativePath,
                                                 @NotNull PackagingElementResolvingContext context,
                                                 @NotNull ArtifactType artifactType,
                                                 @NotNull Processor<PackagingElement<?>> processor) {
    final List<PackagingElement<?>> dirs = findByRelativePath(root, relativePath, context, artifactType);
    for (PackagingElement<?> dir : dirs) {
      if (dir instanceof DirectoryPackagingElement) {
        final List<PackagingElement<?>> children = ((DirectoryPackagingElement)dir).getChildren();
        if (!processElements(children, context, artifactType, processor)) {
          return false;
        }
      }
    }
    return true;
  }
}
