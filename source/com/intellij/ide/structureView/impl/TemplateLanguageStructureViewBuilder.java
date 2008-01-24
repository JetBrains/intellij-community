/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.structureView.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.StructureViewWrapperImpl;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.ide.structureView.*;
import com.intellij.ide.structureView.impl.jsp.StructureViewComposite;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.*;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public abstract class TemplateLanguageStructureViewBuilder implements StructureViewBuilder {
  protected final PsiFile myMainFile;
  private PsiTreeChangeAdapter myPsiTreeChangeAdapter;
  private Language myTemplateDataLanguage;
  private StructureViewComposite.StructureViewDescriptor myBaseStructureViewDescriptor;
  private FileEditor myFileEditor;
  private StructureViewComposite myStructureViewComposite;
  private int myBaseLanguageViewDescriptorIndex;

  protected TemplateLanguageStructureViewBuilder(PsiElement psiElement) {
    myMainFile = PsiUtil.getTemplateLanguageFile(psiElement);
    installBaseLanguageListener();
  }

  protected void installBaseLanguageListener() {
    myPsiTreeChangeAdapter = new PsiTreeChangeAdapter() {
      public void childAdded(PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      public void childRemoved(PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      public void childReplaced(PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      public void childMoved(PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      final Alarm myAlarm = new Alarm();
      public void childrenChanged(PsiTreeChangeEvent event) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(new Runnable(){
          public void run() {
            if (myBaseStructureViewDescriptor != null && ((StructureViewComponent)myBaseStructureViewDescriptor.structureView).getTree() == null) return;
            if (!myMainFile.isValid()) return;
            ApplicationManager.getApplication().runReadAction(new Runnable(){
              public void run() {
                Language baseLanguage = getViewProvider().getTemplateDataLanguage();
                if (baseLanguage == myTemplateDataLanguage) {
                  updateBaseLanguageView();
                }
                else {
                  myTemplateDataLanguage = baseLanguage;
                  StructureViewWrapper structureViewWrapper = StructureViewFactoryEx.getInstanceEx(myMainFile.getProject()).getStructureViewWrapper();
                  ((StructureViewWrapperImpl)structureViewWrapper).rebuild();
                  ((ProjectViewImpl)ProjectView.getInstance(myMainFile.getProject())).rebuildStructureViewPane();
                }
              }
            });
          }
        }, 300, ModalityState.NON_MODAL);
      }
    };
    myTemplateDataLanguage = getViewProvider().getTemplateDataLanguage();
    myMainFile.getManager().addPsiTreeChangeListener(myPsiTreeChangeAdapter);
  }

  private TemplateLanguageFileViewProvider getViewProvider() {
    return (TemplateLanguageFileViewProvider)myMainFile.getViewProvider();
  }

  private void updateBaseLanguageView() {
    if (myBaseStructureViewDescriptor == null || !myMainFile.getProject().isOpen()) return;
    final StructureViewComponent view = (StructureViewComponent)myBaseStructureViewDescriptor.structureView;
    if (view.isDisposed()) return;

    StructureViewState state = view.getState();
    Object[] expandedElements = state.getExpandedElements();
    List<PsiAnchor> expanded = new ArrayList<PsiAnchor>(expandedElements == null ? 0 : expandedElements.length);
    if (expandedElements != null) {
      for (Object element : expandedElements) {
        if (element instanceof PsiElement) {
          expanded.add(new PsiAnchor((PsiElement)element));
        }
      }
    }
    Object[] selectedElements = state.getSelectedElements();
    List<PsiAnchor> selected = new ArrayList<PsiAnchor>(selectedElements == null ? 0 : selectedElements.length);
    if (selectedElements != null) {
      for (Object element : selectedElements) {
        if (element instanceof PsiElement && ((PsiElement)element).isValid()) {
          selected.add(new PsiAnchor((PsiElement)element));
        }
      }
    }
    updateTemplateDataFileView();

    if (view.isDisposed()) return;

    for (PsiAnchor pointer : expanded) {
      PsiElement element = pointer.retrieve();
      if (element != null) {
        view.expandPathToElement(element);
      }
    }
    for (PsiAnchor pointer : selected) {
      PsiElement element = pointer.retrieve();
      if (element != null) {
        view.addSelectionPathTo(element);
      }
    }
  }

  private void removeBaseLanguageListener() {
    myMainFile.getManager().removePsiTreeChangeListener(myPsiTreeChangeAdapter);
  }

  @NotNull
  public StructureView createStructureView(FileEditor fileEditor, Project project) {
    myFileEditor = fileEditor;
    List<StructureViewComposite.StructureViewDescriptor> descriptors = new ArrayList<StructureViewComposite.StructureViewDescriptor>();
    descriptors.add(createMainView(fileEditor, myMainFile));

    myBaseLanguageViewDescriptorIndex = -1;
    final Language dataLanguage = getViewProvider().getTemplateDataLanguage();

    updateTemplateDataFileView();
    if (myBaseStructureViewDescriptor != null) {
      descriptors.add(myBaseStructureViewDescriptor);
      myBaseLanguageViewDescriptorIndex = descriptors.size() - 1;
    }

    for (final Language language : getViewProvider().getRelevantLanguages()) {
      if (language != dataLanguage && language != getViewProvider().getBaseLanguage()) {
        ContainerUtil.addIfNotNull(createBaseLanguageStructureView(fileEditor, language), descriptors);
      }
    }

    StructureViewComposite.StructureViewDescriptor[] array = descriptors.toArray(new StructureViewComposite.StructureViewDescriptor[descriptors.size()]);
    myStructureViewComposite = new StructureViewComposite(array){
      public void dispose() {
        removeBaseLanguageListener();
        super.dispose();
      }
    };
    return myStructureViewComposite;
  }

  protected abstract StructureViewComposite.StructureViewDescriptor createMainView(FileEditor fileEditor, PsiFile mainFile);

  @Nullable
  private StructureViewComposite.StructureViewDescriptor createBaseLanguageStructureView(final FileEditor fileEditor, final Language language) {
    FileType fileType = findFileType(language);
    Project project = myMainFile.getProject();
    final TemplateLanguageFileViewProvider viewProvider = getViewProvider();
    final PsiFile templateLanguagePsiFile = viewProvider.getPsi(language);
    if (templateLanguagePsiFile == null) return null;
    TreeBasedStructureViewBuilder baseViewBuilder = (TreeBasedStructureViewBuilder)LanguageStructureViewBuilder.INSTANCE
      .forLanguage(language).getStructureViewBuilder(templateLanguagePsiFile);
    if (baseViewBuilder == null) return null;

    StructureViewModel modelWrapper = new StructureViewModelWrapper(baseViewBuilder.createStructureViewModel(), myMainFile);
    StructureView structureView = StructureViewFactory.getInstance(project).createStructureView(fileEditor, modelWrapper, project);
    return new StructureViewComposite.StructureViewDescriptor(IdeBundle.message("tab.structureview.baselanguage.view", language.getID()), structureView, fileType.getIcon());
  }

  private void updateTemplateDataFileView() {
    new WriteCommandAction(myMainFile.getProject()) {
      protected void run(Result result) throws Throwable {
        final Language newBaseLanguage = getViewProvider().getTemplateDataLanguage();

        if (myBaseStructureViewDescriptor != null) {
          if (myTemplateDataLanguage == newBaseLanguage) return;

          Disposer.dispose(myBaseStructureViewDescriptor.structureView);
        }

        myBaseStructureViewDescriptor = createBaseLanguageStructureView(myFileEditor, newBaseLanguage);
        if (myStructureViewComposite != null) {
          myStructureViewComposite.setStructureView(myBaseLanguageViewDescriptorIndex, myBaseStructureViewDescriptor);
        }
      }
    }.execute();
  }

  @NotNull
  private static FileType findFileType(final Language language) {
    FileType[] registeredFileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    for (FileType fileType : registeredFileTypes) {
      if (fileType instanceof LanguageFileType && ((LanguageFileType)fileType).getLanguage() == language) {
        return fileType;
      }
    }
    return FileTypes.UNKNOWN;
  }
}
