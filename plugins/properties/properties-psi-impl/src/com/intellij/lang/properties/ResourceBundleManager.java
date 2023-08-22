// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.properties.editor.ResourceBundleAsVirtualFile;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.SingletonNotificationManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
@State(name = "ResourceBundleManager", storages = @Storage("resourceBundles.xml"))
public final class ResourceBundleManager implements PersistentStateComponent<ResourceBundleManagerState>, Disposable {
  private final static Logger LOG = Logger.getInstance(ResourceBundleManager.class);
  private static final String BUNDLE_EDITOR_PLUGIN_ID = "com.intellij.properties.bundle.editor";
  private static final String SUGGEST_RESOURCE_BUNDLE_EDITOR_PLUGIN = "suggest.resource.bundle.editor.plugin";

  private ResourceBundleManagerState myState = new ResourceBundleManagerState();

  private final SingletonNotificationManager myNotificationManager = new SingletonNotificationManager(PluginsAdvertiser.getNotificationGroup().getDisplayId(), 
                                                                                                      NotificationType.INFORMATION);
  
  public ResourceBundleManager(@NotNull Project project) {
    PsiManager manager = PsiManager.getInstance(project);
    manager.addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      @Override
      public void childMoved(@NotNull final PsiTreeChangeEvent event) {
        final PsiElement child = event.getChild();
        if (!(child instanceof PsiFile)) {
          if (child instanceof PsiDirectory) {
            if (event.getOldParent() instanceof PsiDirectory && event.getNewParent() instanceof PsiDirectory) {
              final String fromDirUrl = ((PsiDirectory)event.getOldParent()).getVirtualFile().getUrl() + "/";
              final NotNullLazyValue<String> toDirUrl = NotNullLazyValue.lazy(() -> {
                return ((PsiDirectory)event.getNewParent()).getVirtualFile().getUrl() + "/";
              });
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

        final NotNullLazyValue<Pair<String, String>> oldAndNewUrls = NotNullLazyValue.lazy(() -> {
          final String newUrl = propertiesFile.getVirtualFile().getUrl();
          return new Pair<>(oldParentUrl + newUrl.substring(newParentUrl.length()), newUrl);
        });

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
        if (!(child instanceof PsiFile psiFile)) {
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
        if (!PropertiesImplUtil.canBePropertyFile(psiFile)) return;

        final VirtualFile virtualFile = psiFile.getVirtualFile();
        final NotNullLazyValue<String> url = NotNullLazyValue.lazy(() -> virtualFile.getUrl());
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
    }, this);
  
    project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (file.getFileType() == PropertiesFileType.INSTANCE &&
            PropertiesComponent.getInstance().getBoolean(SUGGEST_RESOURCE_BUNDLE_EDITOR_PLUGIN, true)) {
          PsiFile psiFile = manager.findFile(file);
          if (psiFile != null && !file.getNameWithoutExtension().equals(PropertiesUtil.getDefaultBaseName(psiFile))) {
            PluginId pluginId = PluginId.getId(BUNDLE_EDITOR_PLUGIN_ID);
            IdeaPluginDescriptor pluginDescriptor = PluginManagerCore.getPlugin(pluginId);
            if (pluginDescriptor == null || !pluginDescriptor.isEnabled()) {
              myNotificationManager.notify(IdeBundle.message("plugins.advertiser.plugins.suggestions.title"),
                                           PropertiesBundle.message("notification.content.resource.bundle.plugin.advertisement"), project, notification -> {
                notification.setSuggestionType(true);
                notification.setDisplayId("resource.bundle.editor");
                if (pluginDescriptor == null) {
                  notification.addAction(NotificationAction.createSimpleExpiring(PropertiesBundle.message("notification.content.install.plugin"), () -> {
                    PluginsAdvertiser.installAndEnable(project, Collections.singleton(pluginId), true, () -> {});
                  }));
                }
                else {
                  notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("plugins.advertiser.action.enable.plugin"), 
                                                                                 () -> PluginManagerConfigurable.showPluginConfigurableAndEnable(project, Set.of(pluginDescriptor))));
                }
                notification.addAction(NotificationAction.createSimpleExpiring(
                  PropertiesBundle.message("notification.content.ignore.plugin"),
                  () -> PropertiesComponent.getInstance().setValue(SUGGEST_RESOURCE_BUNDLE_EDITOR_PLUGIN, false)));
              });
            }
          }
        }
      }
    });
  }

  @Override
  public void dispose() {
  }

  public static ResourceBundleManager getInstance(final Project project) {
    return project.getService(ResourceBundleManager.class);
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

  public void combineToResourceBundle(final @NotNull List<? extends PropertiesFile> propertiesFiles, final String baseName) {
    if (propertiesFiles.isEmpty()) {
      throw new IllegalStateException();
    }
    myState.getCustomResourceBundles()
      .add(new CustomResourceBundleState().addAll(ContainerUtil.map(propertiesFiles, file -> file.getVirtualFile().getUrl())).setBaseName(baseName));
  }

  public ResourceBundle combineToResourceBundleAndGet(final @NotNull List<? extends PropertiesFile> propertiesFiles, final String baseName) {
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
  public void loadState(@NotNull ResourceBundleManagerState state) {
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
