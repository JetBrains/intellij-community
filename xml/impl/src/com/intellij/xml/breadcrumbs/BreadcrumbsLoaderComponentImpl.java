/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.xml.breadcrumbs;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author spleaner
 */
public class BreadcrumbsLoaderComponentImpl extends BreadcrumbsLoaderComponent {
  public static final Key<Object> BREADCRUMBS_SUITABLE_FILE = new Key<Object>("breadcrumbs.suitable.file");

  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.breadcrumbs.BreadcrumbsLoaderComponentImpl");
  private Map<Language, BreadcrumbsInfoProvider> myProviders = new HashMap<Language, BreadcrumbsInfoProvider>();

  public BreadcrumbsLoaderComponentImpl(@NotNull final Project project) {
    super(project);
  }

  @Nullable
  BreadcrumbsInfoProvider getInfoProvider(@NotNull final Language language) {
    return myProviders.get(language);
  }

  public void registerInfoProvider(@NotNull final BreadcrumbsInfoProvider provider) {
    for (final Language lang : provider.getLanguages()) {
      if (StdLanguages.TEXT != lang) { // to avoid languages, which are not loaded actually
        if (myProviders.containsKey(lang)) {
          LOG.error("Breadcrumbs info provider for language : " + lang.getID() + " was already registered!");
        }

        myProviders.put(lang, provider);
      }
    }
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "HtmlBreadCrumbsComponent";
  }

  public void initComponent() {
    final MyFileEditorManagerListener listener = new MyFileEditorManagerListener(this);
    Disposer.register(myProject, listener);
    FileEditorManager.getInstance(myProject).addFileEditorManagerListener(listener, listener);

    registerInfoProvider(new XmlLanguageBreadcrumbsInfoProvider());
  }

  public void disposeComponent() {
    myProviders.clear();
  }

  private static class MyFileEditorManagerListener implements FileEditorManagerListener, Disposable {
    private BreadcrumbsLoaderComponentImpl myLoaderComponent;

    public MyFileEditorManagerListener(final BreadcrumbsLoaderComponentImpl loaderComponent) {
      myLoaderComponent = loaderComponent;
    }

    public void dispose() {
      myLoaderComponent = null;
    }

    public void fileOpened(final FileEditorManager source, final VirtualFile file) {
      if (isSuitable(source.getProject(), file)) {
        final FileEditor[] fileEditors = source.getEditors(file);
        for (final FileEditor each : fileEditors) {
          if (each instanceof TextEditor) {
            final BreadcrumbsComponent component = new BreadcrumbsComponent(((TextEditor)each).getEditor(), myLoaderComponent);
            source.addTopComponent(each, component);

            Disposer.register(each, component);
            Disposer.register(each, new Disposable() {
              public void dispose() {
                source.removeTopComponent(each, component);
              }
            });
          }
        }
      }
    }

    private static boolean isSuitable(final Project project, final VirtualFile file) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      return psiFile != null && ((psiFile instanceof XmlFile) || psiFile.getUserData(BREADCRUMBS_SUITABLE_FILE) != null);
    }

    public void fileClosed(final FileEditorManager source, final VirtualFile file) {
    }

    public void selectionChanged(final FileEditorManagerEvent event) {
    }
  }
}