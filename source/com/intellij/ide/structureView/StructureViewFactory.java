package com.intellij.ide.structureView;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

import java.util.List;

/**
 * @author Eugene Belyaev
 */
public abstract class StructureViewFactory {
  public static StructureViewFactory getInstance(Project project) {
    return project.getComponent(StructureViewFactory.class);
  }

  public abstract StructureView getStructureView();

  public abstract void registerExtension(Class<? extends PsiElement> type, StructureViewExtension extension);
  public abstract void unregisterExtension(Class<? extends PsiElement> type, StructureViewExtension extension);

  public abstract List<StructureViewExtension> getAllExtensions(Class<? extends PsiElement> type);

}