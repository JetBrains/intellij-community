/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.util.scopeChooser;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.ide.util.TreeClassChooserDialog;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageView;
import com.intellij.usageView.UsageViewManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class ScopeChooserCombo extends ComboboxWithBrowseButton {
  private Project myProject;
  private boolean mySuggestSearchInLibs;
  private boolean myPrevSearchFiles;

  public ScopeChooserCombo(final Project project, boolean suggestSearchInLibs, boolean prevSearchWholeFiles, String preselect) {
    mySuggestSearchInLibs = suggestSearchInLibs;
    myPrevSearchFiles = prevSearchWholeFiles;
    final JComboBox combo = getComboBox();
    myProject = project;
    addActionListener(createScopeChooserListener());

    combo.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setText(((ScopeDescriptor)value).getDisplay());
        return this;
      }
    });

    rebuildModel();

    selectScope(preselect);
  }

  private void selectScope(String preselect) {
    if (preselect != null) {
      final JComboBox combo = getComboBox();
      DefaultComboBoxModel model = (DefaultComboBoxModel)combo.getModel();
      for (int i = 0; i < model.getSize(); i++) {
        ScopeDescriptor descriptor = (ScopeDescriptor)model.getElementAt(i);
        if (preselect.equals(descriptor.getDisplay())) {
          combo.setSelectedIndex(i);
          break;
        }
      }
    }
  }

  protected ActionListener createScopeChooserListener() {
    return new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ScopeChooserDialog dlg = new ScopeChooserDialog(myProject, NamedScopeManager.getInstance(myProject));
        String selection = getSelectedScopeName();
        if (selection != null) {
          dlg.setSelectedScope(selection);
        }
        dlg.show();
        rebuildModel();
        selectScope(dlg.getSelectedScope());
      }
    };
  }

  protected void rebuildModel() {
    getComboBox().setModel(createModel());
  }

  protected static class ScopeDescriptor {
    private SearchScope myScope;

    public ScopeDescriptor(SearchScope scope) {
      myScope = scope;
    }

    public String getDisplay() {
      return myScope.getDisplayName();
    }

    public SearchScope getScope() {
      return myScope;
    }
  }

  private DefaultComboBoxModel createModel() {
    DefaultComboBoxModel model = new DefaultComboBoxModel();
    createPredefinedScopeDescriptors(model);

    NamedScopeManager scopeManager = NamedScopeManager.getInstance(myProject);
    NamedScope[] scopes = scopeManager.getScopes();
    for (int i = 0; i < scopes.length; i++) {
      NamedScope scope = scopes[i];
      model.addElement(new ScopeDescriptor(GlobalSearchScope.filterScope(myProject, scope, true)));
    }

    return model;
  }

  protected void createPredefinedScopeDescriptors(DefaultComboBoxModel model) {
    model.addElement(new ScopeDescriptor(GlobalSearchScope.projectScope(myProject)));
    if (mySuggestSearchInLibs) {
      model.addElement(new ScopeDescriptor(GlobalSearchScope.allScope(myProject)));
    }
    model.addElement(new ScopeDescriptor(GlobalSearchScope.projectProductionScope(myProject, true)));
    model.addElement(new ScopeDescriptor(GlobalSearchScope.projectTestScope(myProject, true)));

    FileEditorManager fileEditorManager = FileEditorManager.getInstance(getProject());
    if (fileEditorManager.getSelectedTextEditor() != null) {
      model.addElement(new ScopeDescriptor(new LocalSearchScope(PsiDocumentManager.getInstance(getProject()).getPsiFile(
        fileEditorManager.getSelectedTextEditor().getDocument()), "Current File")));

      if (fileEditorManager.getSelectedTextEditor().getSelectionModel().hasSelection()) {
        PsiElement[] elements = CodeInsightUtil.findStatementsInRange(
          PsiDocumentManager.getInstance(getProject()).getPsiFile(fileEditorManager.getSelectedTextEditor().getDocument()),
          fileEditorManager.getSelectedTextEditor().getSelectionModel().getSelectionStart(),
          fileEditorManager.getSelectedTextEditor().getSelectionModel().getSelectionEnd()
        );

        if (elements != null) {
          model.addElement(new ScopeDescriptor(new LocalSearchScope(elements, "Selection")));
        }
      }
    }

    UsageView selection = UsageViewManager.getInstance(getProject()).getSelectedUsageView();

    if (selection != null && !selection.isInAsyncUpdate()) {
      final UsageInfo[] infos = selection.getUsages();
      if (infos != null) {
        final java.util.List<PsiElement> results = new ArrayList<PsiElement>(infos.length);

        if (myPrevSearchFiles) {
          final Set<VirtualFile> files = new HashSet<VirtualFile>();
          for (int i = 0; i < infos.length; i++) {
            UsageInfo info = infos[i];
            if (!selection.isExcluded(info)) {
              PsiElement psiElement = info.getElement();
              if (psiElement != null && psiElement.isValid()) {
                PsiFile psiFile = psiElement.getContainingFile();
                if (psiFile != null) {
                  VirtualFile file = psiFile.getVirtualFile();
                  if (file != null) files.add(file);
                }
              }
            }
          }
          if (files.size() > 0) {
            model.addElement(new ScopeDescriptor(new GlobalSearchScope() {
              public String getDisplayName() {
                return "Files in Previous Search Result";
              }

              public boolean contains(VirtualFile file) {
                return files.contains(file);
              }

              public int compare(VirtualFile file1, VirtualFile file2) {
                return 0;
              }

              public boolean isSearchInModuleContent(Module aModule) {
                return true;
              }

              public boolean isSearchInLibraries() {
                return true;
              }
            }));
          }
        }
        else {
          for (int i = 0; i < infos.length; ++i) {
            if (!selection.isExcluded(infos[i]) &&
                infos[i].getElement() != null &&
                infos[i].getElement().isValid()
            ) {
              results.add(infos[i].getElement());
            }
          }

          if (results.size() > 0) {
            model.addElement(new ScopeDescriptor(new LocalSearchScope(results.toArray(new PsiElement[results.size()]),
                                                                      "Previous Search Results")));
          }
        }
      }
    }

    model.addElement(new ClassHierarchyScopeDescriptor());
  }

  class ClassHierarchyScopeDescriptor extends ScopeDescriptor {
    private SearchScope myCachedScope;

    public ClassHierarchyScopeDescriptor() {
      super(null);
    }

    public String getDisplay() {
      return "Class Hierarchy";
    }

    public SearchScope getScope() {
      if (myCachedScope == null) {
        TreeClassChooserDialog chooser = new TreeClassChooserDialog("Choose Base Class of the Hierarchy to Search In",
                                                                    getProject(),
                                                                    GlobalSearchScope.allScope(getProject()),
                                                                    null,
                                                                    null);

        chooser.show();

        PsiClass aClass = chooser.getSelectedClass();
        if (aClass == null) return null;

        List<PsiElement> classesToSearch = new LinkedList<PsiElement>();
        classesToSearch.add(aClass);

        final PsiManager psiManager = PsiManager.getInstance(myProject);
        final GlobalSearchScope searchScope = GlobalSearchScope.allScope(myProject);

        final PsiClass[] descendants = psiManager.getSearchHelper().findInheritors(aClass,
                                                                                   searchScope,
                                                                                   true);

        classesToSearch.addAll(Arrays.asList(descendants));

        myCachedScope = new LocalSearchScope(classesToSearch.toArray(new PsiElement[classesToSearch.size()]),
                                             "Hierarchy of " + aClass.getQualifiedName());
      }

      return myCachedScope;
    }
  }

  public SearchScope getSelectedScope() {
    JComboBox combo = getComboBox();
    int idx = combo.getSelectedIndex();
    if (idx < 0) return null;
    return ((ScopeDescriptor)combo.getSelectedItem()).getScope();
  }

  public String getSelectedScopeName() {
    JComboBox combo = getComboBox();
    int idx = combo.getSelectedIndex();
    if (idx < 0) return null;
    return ((ScopeDescriptor)combo.getSelectedItem()).getDisplay();
  }

  public Project getProject() {
    return myProject;
  }
}