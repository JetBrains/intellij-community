// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public abstract class DomService {
  private static DomService ourCachedInstance = null;

  public static DomService getInstance() {
    if (ourCachedInstance == null) {
      ourCachedInstance = ApplicationManager.getApplication().getService(DomService.class);
    }
    return ourCachedInstance;
  }

  /**
   * @param rootElementClass class of root (file-level) element in DOM model
   * @param scope            search scope
   * @return files containing given root element
   * @see #getFileElements(Class, Project, GlobalSearchScope)
   */
  public abstract @NotNull Collection<VirtualFile> getDomFileCandidates(@NotNull Class<? extends DomElement> rootElementClass,
                                                               @NotNull GlobalSearchScope scope);

  // used externally
  public @NotNull Collection<VirtualFile> getDomFileCandidates(@NotNull Class<? extends DomElement> rootElementClass,
                                                               Project project,
                                                               @NotNull GlobalSearchScope scope) {
    return getDomFileCandidates(rootElementClass, scope);
  }

  /**
   * @param rootElementClass class of root (file-level) element in DOM model
   * @param project          current project
   * @param scope            search scope
   * @return DOM file elements containing given root element
   */
  public abstract @NotNull <T extends DomElement> List<DomFileElement<T>> getFileElements(@NotNull Class<T> rootElementClass,
                                                                                 @NotNull Project project,
                                                                                 @Nullable GlobalSearchScope scope);

  public abstract @NotNull ModelMerger createModelMerger();

  public abstract <T extends DomElement> DomAnchor<T> createAnchor(T domElement);

  public abstract @NotNull XmlFile getContainingFile(@NotNull DomElement domElement);

  public abstract @NotNull EvaluatedXmlName getEvaluatedXmlName(@NotNull DomElement element);

  public abstract @NotNull XmlFileHeader getXmlFileHeader(@NotNull XmlFile file);

  public enum StructureViewMode {
    SHOW, SHOW_CHILDREN, SKIP
  }

  public abstract @NotNull StructureViewBuilder createSimpleStructureViewBuilder(@NotNull XmlFile file, @NotNull Function<DomElement, StructureViewMode> modeProvider);
}
