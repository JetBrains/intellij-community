/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.properties;

import com.intellij.lang.properties.editor.ResourceBundleAsVirtualFile;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
@State(name = "ResourceBundleManager", storages = @Storage("resourceBundles.xml"))
public class ResourceBundleManager implements PersistentStateComponent<ResourceBundleManagerState> {
  private final static Logger LOG = Logger.getInstance(ResourceBundleManager.class);

  private ResourceBundleManagerState myState = new ResourceBundleManagerState();

  public ResourceBundleManager(final PsiManager manager) {
    manager.addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      @Override
      public void childMoved(@NotNull final PsiTreeChangeEvent event) {
        final PsiElement child = event.getChild();
        if (!(child instanceof PsiFile)) {
          if (child instanceof PsiDirectory) {
            if (event.getOldParent() instanceof PsiDirectory && event.getNewParent() instanceof PsiDirectory) {
              final String fromDirUrl = ((PsiDirectory)event.getOldParent()).getVirtualFile().getUrl() + "/";
              final NotNullLazyValue<String> toDirUrl = new NotNullLazyValue<String>() {
                @NotNull
                @Override
                protected String compute() {
                  return ((PsiDirectory)event.getNewParent()).getVirtualFile().getUrl() + "/";
                }
              };
              for (String dissociatedFileUrl : new SmartList<>(myState.getDissociatedFiles())) {
                if (dissociatedFileUrl.startsWith(fromDirUrl)) {
                  myState.getDissociatedFiles().remove(dissociatedFileUrl);
                  myState.getDissociatedFiles().add(toDirUrl.getValue() + dissociatedFileUrl.substring(fromDirUrl.length()));
                }
              }
              for (CustomResourceBundleState customResourceBundleState : myState.getCustomResourceBundles()) {
                for (String fileUrl : new SmartList<>(customResourceBundleState.getFileUrls())) {
                  if (fileUrl.startsWith(fromDirUrl)) {
                    customResourceBundleState.getFileUrls().remove(fileUrl);
                    customResourceBundleState.getFileUrls().add(toDirUrl.getValue() + fileUrl.substring(fromDirUrl.length()));
                  }
                }
              }
            }
          }
          return;
        }
        final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile((PsiFile)child);
        if (propertiesFile == null) {
          return;
        }
        final String oldParentUrl = getUrl(event.getOldParent());
        if (oldParentUrl == null) {
          return;
        }
        final String newParentUrl = getUrl(event.getNewParent());
        if (newParentUrl == null) {
          return;
        }

        final NotNullLazyValue<Pair<String, String>> oldAndNewUrls = new NotNullLazyValue<Pair<String, String>>() {
          @NotNull
          @Override
          protected Pair<String, String> compute() {
            final String newUrl = propertiesFile.getVirtualFile().getUrl();
            return Pair.create(oldParentUrl + newUrl.substring(newParentUrl.length()), newUrl);
          }
        };

        if (!myState.getDissociatedFiles().isEmpty()) {
          if (myState.getDissociatedFiles().remove(oldAndNewUrls.getValue().getFirst())) {
            myState.getDissociatedFiles().add(oldAndNewUrls.getValue().getSecond());
          }
        }

        for (CustomResourceBundleState customResourceBundleState : myState.getCustomResourceBundles()) {
          if (customResourceBundleState.getFileUrls().remove(oldAndNewUrls.getValue().getFirst())) {
            customResourceBundleState.getFileUrls().add(oldAndNewUrls.getValue().getSecond());
            break;
          }
        }

      }

      @Nullable
      private String getUrl(PsiElement element) {
        return !(element instanceof PsiDirectory) ? null : ((PsiDirectory)element).getVirtualFile().getUrl();
      }

      @Override
      public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
        final PsiElement child = event.getChild();
        if (!(child instanceof PsiFile)) {
          if (child instanceof PsiDirectory) {
            final String deletedDirUrl = ((PsiDirectory)child).getVirtualFile().getUrl() + "/";
            for (String dissociatedFileUrl : new SmartList<>(myState.getDissociatedFiles())) {
              if (dissociatedFileUrl.startsWith(deletedDirUrl)) {
                myState.getDissociatedFiles().remove(dissociatedFileUrl);
              }
            }
            for (CustomResourceBundleState customResourceBundleState : new SmartList<>(myState.getCustomResourceBundles())) {
              for (String fileUrl : new ArrayList<>(customResourceBundleState.getFileUrls())) {
                if (fileUrl.startsWith(deletedDirUrl)) {
                  customResourceBundleState.getFileUrls().remove(fileUrl);
                }
              }
              if (customResourceBundleState.getFileUrls().size() < 2) {
                myState.getCustomResourceBundles().remove(customResourceBundleState);
              }
            }
          }
          return;
        }
        PsiFile psiFile = (PsiFile)child;
        if (!PropertiesImplUtil.canBePropertyFile(psiFile)) return;

        final VirtualFile virtualFile = psiFile.getVirtualFile();
        final NotNullLazyValue<String> url = new NotNullLazyValue<String>() {
          @NotNull
          @Override
          protected String compute() {
            return virtualFile.getUrl();
          }
        };
        if (!myState.getDissociatedFiles().isEmpty()) {
          myState.getDissociatedFiles().remove(url.getValue());
        }
        for (CustomResourceBundleState customResourceBundleState : new SmartList<>(myState.getCustomResourceBundles())) {
          final Set<String> urls = customResourceBundleState.getFileUrls();
          if (urls.remove(url.getValue())) {
            if (urls.size() < 2) {
              myState.getCustomResourceBundles().remove(customResourceBundleState);
            }
            break;
          }
        }
      }
    });
  }

  public static ResourceBundleManager getInstance(final Project project) {
    return ServiceManager.getService(project, ResourceBundleManager.class);
  }

  @Nullable
  public String getFullName(final @NotNull PropertiesFile propertiesFile) {
    return ReadAction.compute(() -> {
      final PsiDirectory directory = propertiesFile.getParent();
      final String packageQualifiedName = PropertiesUtil.getPackageQualifiedName(directory);
      if (packageQualifiedName == null) {
        return null;
      }
      final StringBuilder qName = new StringBuilder(packageQualifiedName);
      if (qName.length() > 0) {
        qName.append(".");
      }
      qName.append(getBaseName(propertiesFile.getContainingFile()));
      return qName.toString();
    });
  }

  @NotNull
  public String getBaseName(@NotNull final PsiFile file) {
    final VirtualFile vFile = file.getVirtualFile();
    final CustomResourceBundleState customResourceBundle = getCustomResourceBundleState(vFile);
    if (customResourceBundle != null) {
      return customResourceBundle.getBaseName();
    }
    if (isDefaultDissociated(vFile)) {
      return vFile.getNameWithoutExtension();
    }
    return PropertiesUtil.getDefaultBaseName(file);
  }


  public void dissociateResourceBundle(final @NotNull ResourceBundle resourceBundle) {
    closeResourceBundleEditors(resourceBundle);
    if (resourceBundle instanceof CustomResourceBundle) {
      final CustomResourceBundleState state =
        getCustomResourceBundleState(resourceBundle.getDefaultPropertiesFile().getVirtualFile());
      LOG.assertTrue(state != null);
      myState.getCustomResourceBundles().remove(state);
    } else {
      if (EmptyResourceBundle.getInstance() != resourceBundle) {
        ((ResourceBundleImpl) resourceBundle).invalidate();
      }
      for (final PropertiesFile propertiesFile : resourceBundle.getPropertiesFiles()) {
        final VirtualFile file = propertiesFile.getContainingFile().getVirtualFile();
        myState.getDissociatedFiles().add(file.getUrl());
      }
    }
  }

  public void combineToResourceBundle(final @NotNull List<PropertiesFile> propertiesFiles, final String baseName) {
    if (propertiesFiles.isEmpty()) {
      throw new IllegalStateException();
    }
    myState.getCustomResourceBundles()
      .add(new CustomResourceBundleState().addAll(ContainerUtil.map(propertiesFiles, file -> file.getVirtualFile().getUrl())).setBaseName(baseName));
  }

  public ResourceBundle combineToResourceBundleAndGet(final @NotNull List<PropertiesFile> propertiesFiles, final String baseName) {
    combineToResourceBundle(propertiesFiles, baseName);
    return propertiesFiles.get(0).getResourceBundle();
  }

  @Nullable
  public CustomResourceBundle getCustomResourceBundle(final @NotNull PropertiesFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    final CustomResourceBundleState state = getCustomResourceBundleState(virtualFile);
    return state == null ? null : CustomResourceBundle.fromState(state, file.getProject());
  }

  public boolean isDefaultDissociated(final @NotNull VirtualFile virtualFile) {
    if (myState.getDissociatedFiles().isEmpty() && myState.getCustomResourceBundles().isEmpty()) {
      return false;
    }
    final String url = virtualFile.getUrl();
    return myState.getDissociatedFiles().contains(url) || getCustomResourceBundleState(virtualFile) != null;
  }

  @Nullable
  private CustomResourceBundleState getCustomResourceBundleState(final @NotNull VirtualFile virtualFile) {
    if (myState.getCustomResourceBundles().isEmpty()) {
      return null;
    }
    final String url = virtualFile.getUrl();
    for (CustomResourceBundleState customResourceBundleState : myState.getCustomResourceBundles()) {
      if (customResourceBundleState.getFileUrls().contains(url)) {
        return customResourceBundleState;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public ResourceBundleManagerState getState() {
    return myState.isEmpty() ? null : myState;
  }

  @Override
  public void loadState(ResourceBundleManagerState state) {
    myState = state.removeNonExistentFiles();
  }

  private static void closeResourceBundleEditors(@NotNull ResourceBundle resourceBundle) {
    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(resourceBundle.getProject());
    fileEditorManager.closeFile(new ResourceBundleAsVirtualFile(resourceBundle));
    for (final PropertiesFile propertiesFile : resourceBundle.getPropertiesFiles()) {
      fileEditorManager.closeFile(propertiesFile.getVirtualFile());
    }
  }
}
