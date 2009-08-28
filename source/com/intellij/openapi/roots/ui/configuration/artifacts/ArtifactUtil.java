package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.elements.*;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class ArtifactUtil {
  private ArtifactUtil() {
  }

  public static CompositePackagingElement<?> copyFromRoot(@NotNull CompositePackagingElement<?> oldRoot, @NotNull Project project) {
    final CompositePackagingElement<?> newRoot = (CompositePackagingElement<?>)copyElement(oldRoot, project);
    copyChildren(oldRoot, newRoot, project);
    return newRoot;
  }


  public static void copyChildren(CompositePackagingElement<?> oldParent, CompositePackagingElement<?> newParent, @NotNull Project project) {
    for (PackagingElement<?> child : oldParent.getChildren()) {
      newParent.addOrFindChild(copyWithChildren(child, project));
    }
  }

  @NotNull
  public static <S> PackagingElement<S> copyWithChildren(@NotNull PackagingElement<S> element, @NotNull Project project) {
    final PackagingElement<S> copy = copyElement(element, project);
    if (element instanceof CompositePackagingElement<?>) {
      copyChildren((CompositePackagingElement<?>)element, (CompositePackagingElement<?>)copy, project);
    }
    return copy;
  }

  @NotNull
  private static <S> PackagingElement<S> copyElement(@NotNull PackagingElement<S> element, @NotNull Project project) {
    //noinspection unchecked
    final PackagingElement<S> copy = (PackagingElement<S>)element.getType().createEmpty(project);
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

  public static <E extends PackagingElement<?>> boolean processPackagingElements(final PackagingElement<?> rootElement, @Nullable PackagingElementType<E> type,
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
    //noinspection unchecked
    to.loadState((S)from.getState());
  }

  @Nullable
  public static String getDefaultArtifactOutputPath(@NotNull String artifactName, final @NotNull Project project) {
    final CompilerProjectExtension extension = CompilerProjectExtension.getInstance(project);
    if (extension == null) return null;
    final String outputUrl = extension.getCompilerOutputUrl();
    if (outputUrl == null) return null;
    return VfsUtil.urlToPath(outputUrl) + "/artifacts/" + FileUtil.sanitizeFileName(artifactName);
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
          if (!fileCopy.isDirectory() && firstName.equals(fileCopy.getOutputFileName())) {
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

  public static Collection<? extends Artifact> findArtifactsByFile(@NotNull final VirtualFile file, @NotNull Project project) {
    final List<Artifact> artifacts = new ArrayList<Artifact>();
    for (final Artifact artifact : ArtifactManager.getInstance(project).getArtifacts()) {
      processPackagingElements(artifact, PackagingElementFactoryImpl.FILE_COPY_ELEMENT_TYPE, new Processor<FileCopyPackagingElement>() {
        public boolean process(FileCopyPackagingElement fileCopyPackagingElement) {
          final VirtualFile root = fileCopyPackagingElement.findFile();
          if (VfsUtil.isAncestor(root, file, false)) {
            artifacts.add(artifact);
            return false;
          }
          return true;
        }
      }, ArtifactManager.getInstance(project).getResolvingContext(), true);
    }
    return artifacts;
  }

  @Nullable
  public static VirtualFile findSourceFileByOutputPath(Artifact artifact, String outputPath, PackagingElementResolvingContext context) {
    final List<VirtualFile> files = findSourceFilesByOutputPath(artifact.getRootElement(), outputPath, context, artifact.getArtifactType());
    return files.isEmpty() ? null : files.get(0);
  }

  @Nullable
  public static VirtualFile findSourceFileByOutputPath(CompositePackagingElement<?> parent, String outputPath,
                                                 PackagingElementResolvingContext context, ArtifactType artifactType) {
    final List<VirtualFile> files = findSourceFilesByOutputPath(parent, outputPath, context, artifactType);
    return files.isEmpty() ? null : files.get(0);
  }

  public static List<VirtualFile> findSourceFilesByOutputPath(CompositePackagingElement<?> parent, String outputPath,
                                                 PackagingElementResolvingContext context, ArtifactType artifactType) {
    outputPath = StringUtil.trimStart(outputPath, "/");
    if (outputPath.length() == 0) {
      return Collections.emptyList();
    }

    int i = outputPath.indexOf('/');
    final String firstName = i != -1 ? outputPath.substring(0, i) : outputPath;
    String tail = i != -1 ? outputPath.substring(i+1) : "";

    final List<CompositePackagingElement<?>> compositeChildren = new SmartList<CompositePackagingElement<?>>();
    final List<FileCopyPackagingElement> fileCopies = new SmartList<FileCopyPackagingElement>();
    final List<FileCopyPackagingElement> dirCopies = new SmartList<FileCopyPackagingElement>();
    ArtifactUtil.processElements(parent.getChildren(), context, artifactType, new Processor<PackagingElement<?>>() {
      public boolean process(PackagingElement<?> element) {
        if (element instanceof CompositePackagingElement) {
          final CompositePackagingElement<?> compositeElement = (CompositePackagingElement<?>)element;
          if (firstName.equals(compositeElement.getName())) {
            compositeChildren.add(compositeElement);
          }
        }
        else if (element instanceof FileCopyPackagingElement) {
          final FileCopyPackagingElement fileCopyElement = (FileCopyPackagingElement)element;
          if (fileCopyElement.isDirectory()) {
            dirCopies.add(fileCopyElement);
          }
          else if (firstName.equals(fileCopyElement.getOutputFileName())) {
            fileCopies.add(fileCopyElement);
          }
        }
        return true;
      }
    });

    List<VirtualFile> result = new SmartList<VirtualFile>();
    for (CompositePackagingElement<?> child : compositeChildren) {
      result.addAll(findSourceFilesByOutputPath(child, tail, context, artifactType));
    }
    if (tail.length() == 0) {
      for (FileCopyPackagingElement fileCopy : fileCopies) {
        ContainerUtil.addIfNotNull(fileCopy.findFile(), result);
      }
    }
    for (FileCopyPackagingElement dirCopy : dirCopies) {
      final VirtualFile sourceRoot = dirCopy.findFile();
      if (sourceRoot != null) {
        ContainerUtil.addIfNotNull(sourceRoot.findFileByRelativePath(outputPath), result);
      }
    }
    return result;
  }

  public static boolean processParents(@NotNull Artifact artifact,
                                       @NotNull PackagingElementResolvingContext context,
                                       @NotNull ParentElementProcessor processor,
                                       int maxLevel) {
    return processParents(artifact, context, processor, FList.<Pair<Artifact, CompositePackagingElement<?>>>emptyList(), maxLevel,
                          new HashSet<Artifact>());
  }

  private static boolean processParents(@NotNull final Artifact artifact, @NotNull final PackagingElementResolvingContext context,
                                        @NotNull final ParentElementProcessor processor, FList<Pair<Artifact, CompositePackagingElement<?>>> pathToElement,
                                        final int maxLevel, final Set<Artifact> processed) {
    if (!processed.add(artifact)) return true;

    final FList<Pair<Artifact, CompositePackagingElement<?>>> pathFromRoot;
    final CompositePackagingElement<?> rootElement = artifact.getRootElement();
    if (rootElement instanceof ArtifactRootElement<?>) {
      pathFromRoot = pathToElement;
    }
    else {
      if (!processor.process(rootElement, pathToElement, artifact)) {
        return false;
      }
      pathFromRoot = pathToElement.prepend(new Pair<Artifact, CompositePackagingElement<?>>(artifact, rootElement));
    }
    if (pathFromRoot.size() > maxLevel) return true;

    for (final Artifact anArtifact : context.getArtifactModel().getArtifacts()) {
      if (processed.contains(anArtifact)) continue;

      final PackagingElementProcessor<ArtifactPackagingElement> elementProcessor =
          new PackagingElementProcessor<ArtifactPackagingElement>() {
            @Override
            public boolean shouldProcessSubstitution(ComplexPackagingElement<?> element) {
              return !(element instanceof ArtifactPackagingElement);
            }

            @Override
            public boolean process(@NotNull List<CompositePackagingElement<?>> parents, @NotNull ArtifactPackagingElement element) {
              if (artifact.getName().equals(element.getArtifactName())) {
                FList<Pair<Artifact, CompositePackagingElement<?>>> currentPath = pathFromRoot;
                for (int i = 0, parentsSize = parents.size(); i < parentsSize - 1; i++) {
                  CompositePackagingElement<?> parent = parents.get(i);
                  if (!processor.process(parent, currentPath, anArtifact)) {
                    return false;
                  }
                  currentPath = currentPath.prepend(new Pair<Artifact, CompositePackagingElement<?>>(anArtifact, parent));
                  if (currentPath.size() > maxLevel) {
                    return true;
                  }
                }

                if (!parents.isEmpty()) {
                  CompositePackagingElement<?> lastParent = parents.get(parents.size() - 1);
                  if (lastParent instanceof ArtifactRootElement<?> && !processor.process(lastParent, currentPath, anArtifact)) {
                    return false;
                  }
                }
                return processParents(anArtifact, context, processor, currentPath, maxLevel, processed);
              }
              return true;
            }
          };
      if (!processPackagingElements(anArtifact, ArtifactElementType.ARTIFACT_ELEMENT_TYPE, elementProcessor, context, true)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isArchiveName(String name) {
    return name.length() >= 4 && name.charAt(name.length() - 4) == '.' && StringUtil.endsWithIgnoreCase(name, "ar");
  }
}
