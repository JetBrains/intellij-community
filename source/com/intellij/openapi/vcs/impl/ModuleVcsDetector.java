package com.intellij.openapi.vcs.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author yole
 */
public class ModuleVcsDetector implements ProjectComponent {
  private Project myProject;
  private MessageBus myMessageBus;
  private ProjectLevelVcsManagerImpl myVcsManager;

  public ModuleVcsDetector(final Project project, final MessageBus messageBus, final ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myMessageBus = messageBus;
    myVcsManager = (ProjectLevelVcsManagerImpl) vcsManager;
  }

  public void projectOpened() {
    final StartupManager manager = StartupManager.getInstance(myProject);
    manager.registerStartupActivity(new Runnable() {
      public void run() {
        if (myVcsManager.needAutodetectMappings()) {
          autoDetectVcsMappings();
        }
        myVcsManager.updateActiveVcss();
      }
    });
    manager.registerPostStartupActivity(new Runnable() {
      public void run() {
        if (myMessageBus != null) {
          myMessageBus.connect().subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
            public void moduleAdded(final Project project, final Module module) {
              autoDetectModuleVcsMapping(module);
            }

            public void beforeModuleRemoved(final Project project, final Module module) {
              checkRemoveVcsRoot(module);
            }
          });
        }
      }
    });
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ModuleVcsDetector";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private void autoDetectVcsMappings() {
    Set<AbstractVcs> usedVcses = new HashSet<AbstractVcs>();
    Map<VirtualFile, AbstractVcs> vcsMap = new HashMap<VirtualFile, AbstractVcs>();
    for(Module module: ModuleManager.getInstance(myProject).getModules()) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
      for(VirtualFile file: files) {
        AbstractVcs contentRootVcs = myVcsManager.findVersioningVcs(file);
        if (contentRootVcs != null) {
          vcsMap.put(file, contentRootVcs);
        }
        usedVcses.add(contentRootVcs);
      }
    }
    if (usedVcses.size() == 1) {
      final AbstractVcs[] abstractVcses = usedVcses.toArray(new AbstractVcs[1]);
      if (abstractVcses [0] != null) {
        myVcsManager.setAutoDirectoryMapping("", abstractVcses [0].getName());
      }
    }
    else {
      for(Map.Entry<VirtualFile, AbstractVcs> entry: vcsMap.entrySet()) {
        myVcsManager.setAutoDirectoryMapping(entry.getKey().getPath(), entry.getValue() == null ? "" : entry.getValue().getName());
      }
      myVcsManager.cleanupMappings();
    }
  }

  private void autoDetectModuleVcsMapping(final Module module) {
    final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
    for(VirtualFile file: files) {
      AbstractVcs vcs = myVcsManager.findVersioningVcs(file);
      if (vcs != null && vcs != myVcsManager.getVcsFor(file)) {
        myVcsManager.setAutoDirectoryMapping(file.getPath(), vcs.getName());
      }
    }
    myVcsManager.cleanupMappings();
    myVcsManager.updateActiveVcss();
  }

  private void checkRemoveVcsRoot(final Module module) {
    final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
    final String moduleName = module.getName();
    for(final VirtualFile file: files) {
      for(final VcsDirectoryMapping mapping: myVcsManager.getDirectoryMappings()) {
        if (FileUtil.toSystemIndependentName(mapping.getDirectory()).equals(file.getPath())) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              if (myProject.isDisposed()) return;
              final String msg = VcsBundle.message("vcs.root.remove.prompt", FileUtil.toSystemDependentName(file.getPath()), moduleName);
              int rc = Messages.showYesNoDialog(myProject, msg, VcsBundle.message("vcs.root.remove.title"), Messages.getQuestionIcon());
              if (rc == 0) {
                myVcsManager.removeDirectoryMapping(mapping);
                myVcsManager.updateActiveVcss();
              }
            }
          }, ModalityState.NON_MODAL);
          break;
        }
      }
    }
  }


}