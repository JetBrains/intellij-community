/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.palette;

import com.intellij.ide.palette.PaletteGroup;
import com.intellij.ide.palette.PaletteItemProvider;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.ClassUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import org.jetbrains.annotations.NonNls;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * @author yole
 */
public class UIDesignerPaletteProvider implements PaletteItemProvider, ProjectComponent {
  private Project myProject;
  private Palette myPalette;
  private PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private MyRefactoringListenerProvider myRefactoringListenerProvider = new MyRefactoringListenerProvider();
  @NonNls private static final String PROPERTY_GROUPS = "groups";

  public UIDesignerPaletteProvider(Project project, final Palette palette) {
    myProject = project;
    myPalette = palette;
    myPalette.addListener(new Palette.Listener() {
      public void groupsChanged(Palette palette) {
        fireGroupsChanged();
      }
    });

  }

  private void fireGroupsChanged() {
    myPropertyChangeSupport.firePropertyChange(PROPERTY_GROUPS, null, null);
  }

  public PaletteGroup[] getActiveGroups(VirtualFile vFile) {
    if (vFile.getFileType().equals(StdFileTypes.GUI_DESIGNER_FORM)) {
      return myPalette.getToolWindowGroups();
    }
    return PaletteGroup.EMPTY_ARRAY;
  }

  public void addListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  public void projectOpened() {
    RefactoringListenerManager.getInstance(myProject).addListenerProvider(myRefactoringListenerProvider);
  }

  public void projectClosed() {
    RefactoringListenerManager.getInstance(myProject).removeListenerProvider(myRefactoringListenerProvider);
  }

  @NonNls public String getComponentName() {
    return "UIDesignerPaletteProvider";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private class MyRefactoringListenerProvider implements RefactoringElementListenerProvider {
    public RefactoringElementListener getListener(PsiElement element) {
      if (element instanceof PsiClass) {
        PsiClass psiClass = (PsiClass) element;
        final String oldName = ClassUtil.getJVMClassName(psiClass);
        if (oldName != null) {
          final ComponentItem item = myPalette.getItem(oldName);
          if (item != null) {
            return new MyRefactoringElementListener(item);
          }
        }
      }
      return null;
    }

    private class MyRefactoringElementListener implements RefactoringElementListener {
      private ComponentItem myItem;

      public MyRefactoringElementListener(final ComponentItem item) {
        myItem = item;
      }

      public void elementMoved(PsiElement newElement) {
        elementRenamed(newElement);
      }

      public void elementRenamed(PsiElement newElement) {
        PsiClass psiClass = (PsiClass) newElement;
        final String qName = ClassUtil.getJVMClassName(psiClass);
        if (qName != null) {
          myItem.setClassName(qName);
          fireGroupsChanged();
        }
      }
    }
  }
}
