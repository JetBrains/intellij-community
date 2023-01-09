// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.buildtool.MavenImportSpec;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.importing.MavenImportingManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static org.jetbrains.idea.maven.project.MavenGeneralSettingsWatcher.registerGeneralSettingsWatcher;

public final class MavenProjectsManagerWatcher {

  private static final Logger LOG = Logger.getInstance(MavenProjectsManagerWatcher.class);

  private final Project myProject;
  private MavenProjectsTree myProjectsTree;
  private final MavenGeneralSettings myGeneralSettings;
  private final MavenProjectsProcessor myReadingProcessor;
  private final MavenProjectsAware myProjectsAware;
  private final ExecutorService myBackgroundExecutor;
  private final Disposable myDisposable;

  public MavenProjectsManagerWatcher(Project project,
                                     MavenProjectsTree projectsTree,
                                     MavenGeneralSettings generalSettings,
                                     MavenProjectsProcessor readingProcessor) {
    myBackgroundExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("MavenProjectsManagerWatcher.backgroundExecutor", 1);
    myProject = project;
    myProjectsTree = projectsTree;
    myGeneralSettings = generalSettings;
    myReadingProcessor = readingProcessor;
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
    myProjectsAware = new MavenProjectsAware(project, projectsManager, this, myBackgroundExecutor);
    myDisposable = Disposer.newDisposable(projectsManager, MavenProjectsManagerWatcher.class.toString());
  }

  public synchronized void start() {
    MessageBusConnection busConnection = myProject.getMessageBus().connect(myDisposable);
    busConnection.subscribe(ProjectTopics.MODULES, new MavenRenameModulesWatcher());
    busConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyRootChangesListener());
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
    registerGeneralSettingsWatcher(projectsManager, this, myBackgroundExecutor, myDisposable);
    ExternalSystemProjectTracker projectTracker = ExternalSystemProjectTracker.getInstance(myProject);
    projectTracker.register(myProjectsAware, projectsManager);
    projectTracker.activate(myProjectsAware.getProjectId());
  }

  @TestOnly
  public synchronized void enableAutoImportInTests() {
    AutoImportProjectTracker.enableAutoReloadInTests(myDisposable);
  }

  public synchronized void stop() {
    Disposer.dispose(myDisposable);
  }

  public synchronized void addManagedFilesWithProfiles(List<VirtualFile> files, MavenExplicitProfiles explicitProfiles) {
    myProjectsTree.addManagedFilesWithProfiles(files, explicitProfiles);
    scheduleUpdateAll(new MavenImportSpec(false, true, true));
  }


  public void setProjectsTree(MavenProjectsTree tree) {
    myProjectsTree = tree;
  }

  @TestOnly
  public synchronized void resetManagedFilesAndProfilesInTests(List<VirtualFile> files, MavenExplicitProfiles explicitProfiles) {
    myProjectsTree.resetManagedFilesAndProfiles(files, explicitProfiles);
    scheduleUpdateAll(new MavenImportSpec(false, true, true));
  }

  public synchronized void removeManagedFiles(List<VirtualFile> files) {
    myProjectsTree.removeManagedFiles(files);
    scheduleUpdateAll(new MavenImportSpec(false, true, true));
  }

  public synchronized void setExplicitProfiles(MavenExplicitProfiles profiles) {
    myProjectsTree.setExplicitProfiles(profiles);
    scheduleUpdateAll(new MavenImportSpec(false, false, false));
  }

  /**
   * Returned {@link Promise} instance isn't guarantied to be marked as rejected in all cases where importing wasn't performed (e.g.
   * if project is closed)
   */
  public Promise<Void> scheduleUpdateAll(MavenImportSpec spec) {
    if (MavenUtil.isLinearImportEnabled()) {
      return MavenImportingManager.getInstance(myProject).scheduleImportAll(spec).getFinishPromise().then(it -> null);
    }

    final AsyncPromise<Void> promise = new AsyncPromise<>();
    // display all import activities using the same build progress
    MavenSyncConsole.startTransaction(myProject);
    try {
      Runnable onCompletion = createScheduleImportAction(spec, promise);
      scheduleReadingTask(new MavenProjectsProcessorReadingTask(spec.isForceReading(), myProjectsTree, myGeneralSettings, onCompletion));
    }
    finally {
      if (!spec.isForceResolve()) {
        promise.onProcessed(unused -> MavenSyncConsole.finishTransaction(myProject));
      }
    }
    return promise;
  }

  public Promise<Void> scheduleUpdate(List<VirtualFile> filesToUpdate,
                                      List<VirtualFile> filesToDelete,
                                      MavenImportSpec spec) {

    if (MavenUtil.isLinearImportEnabled()) {
      return MavenImportingManager.getInstance(myProject).scheduleUpdate(filesToUpdate, filesToDelete, spec).getFinishPromise().then(it -> null);
    }
    final AsyncPromise<Void> promise = new AsyncPromise<>();
    // display all import activities using the same build progress
    MavenSyncConsole.startTransaction(myProject);
    try {
      Runnable onCompletion = createScheduleImportAction(spec, promise);
      if (LOG.isDebugEnabled()) {
        String withForceOptionMessage = spec.isForceReading() ? " with force option" : "";
        LOG.debug("Scheduling update for " + myProjectsTree + withForceOptionMessage +
                  ". Files to update: " + filesToUpdate + ". Files to delete: " + filesToDelete);
      }

      scheduleReadingTask(new MavenProjectsProcessorReadingTask(
        filesToUpdate, filesToDelete, spec.isForceReading(), myProjectsTree, myGeneralSettings, onCompletion));
    }
    finally {
      if (!spec.isForceResolve()) {
        promise.onProcessed(unused -> MavenSyncConsole.finishTransaction(myProject));
      }
    }
    return promise;
  }

  /**
   * All changed documents must be saved before reading
   */
  private void scheduleReadingTask(@NotNull MavenProjectsProcessorReadingTask readingTask) {
    myReadingProcessor.scheduleTask(readingTask);
  }

  @NotNull
  private Runnable createScheduleImportAction(MavenImportSpec spec, final AsyncPromise<Void> promise) {
    return () -> {
      if (myProject.isDisposed()) {
        promise.setError("Project disposed");
        return;
      }

      if (spec.isForceResolve()) {
        MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
        projectsManager.scheduleImportAndResolve(spec)
          .onSuccess(modules -> promise.setResult(null))
          .onError(t -> promise.setError(t));
      }
      else {
        promise.setResult(null);
      }
    };
  }

  private class MyRootChangesListener implements ModuleRootListener {
    @Override
    public void rootsChanged(@NotNull ModuleRootEvent event) {
      // todo is this logic necessary?
      List<VirtualFile> existingFiles = myProjectsTree.getProjectsFiles();
      List<VirtualFile> newFiles = new ArrayList<>();
      List<VirtualFile> deletedFiles = new ArrayList<>();

      for (VirtualFile f : myProjectsTree.getExistingManagedFiles()) {
        if (!existingFiles.contains(f)) {
          newFiles.add(f);
        }
      }

      for (VirtualFile f : existingFiles) {
        if (!f.isValid()) deletedFiles.add(f);
      }

      if (!deletedFiles.isEmpty() || !newFiles.isEmpty()) {
        scheduleUpdate(newFiles, deletedFiles, new MavenImportSpec(false, false, true));
      }
    }
  }

  private static class MavenRenameModulesWatcher implements ModuleListener {
    @Override
    public void modulesRenamed(@NotNull Project project,
                               @NotNull List<? extends Module> modules,
                               @NotNull Function<? super Module, String> oldNameProvider) {
      for (var module : modules) {
        new MavenRenameModuleHandler(project, module, oldNameProvider.fun(module)).handleModuleRename();
      }
    }

    private static class MavenRenameModuleHandler {
      private final Project myProject;
      private final Module myModule;
      private final String myOldName;
      private final String myNewName;
      private String myGroupId;
      private final MavenProjectsManager myProjectsManager;
      private final List<MavenProject> myUpdatedMavenProjects = new ArrayList<>();

      private MavenRenameModuleHandler(@NotNull Project project,
                                       @NotNull Module module,
                                       @NotNull String oldName) {
        myProject = project;
        myModule = module;
        myOldName = oldName;

        // handle module groups: group.subgroup.module
        var myNewNameHierarchy = module.getName().split("\\.");
        myNewName = myNewNameHierarchy[myNewNameHierarchy.length - 1];

        myProjectsManager = MavenProjectsManager.getInstance(project);
      }

      private boolean replaceArtifactId(@Nullable XmlTag parentTag) {
        if (null == parentTag) return false;
        var artifactIdTag = parentTag.findFirstSubTag("artifactId");
        if (null == artifactIdTag) return false;
        var groupIdTag = parentTag.findFirstSubTag("groupId");
        if (null == groupIdTag) return false;
        if (myGroupId.equals(groupIdTag.getValue().getText()) && myOldName.equals(artifactIdTag.getValue().getText())) {
          artifactIdTag.getValue().setText(myNewName);
          return true;
        }
        return false;
      }

      private boolean replaceModuleArtifactId(MavenDomProjectModel mavenModel) {
        var artifactIdTag = mavenModel.getArtifactId().getXmlTag();
        if (null != artifactIdTag) {
          if (myOldName.equals(artifactIdTag.getValue().getText())) {
            artifactIdTag.getValue().setText(myNewName);
            return true;
          }
        }
        return false;
      }

      private boolean replaceArtifactIdReferences(MavenDomProjectModel mavenModel) {
        var replaced = false;
        if (null != mavenModel.getXmlTag()) {
          // parent artifactId
          replaced = replaceArtifactId(mavenModel.getXmlTag().findFirstSubTag("parent"));
        }

        // dependencies and exclusions
        var dependencies = mavenModel.getDependencies();
        for (var dependency : dependencies.getDependencies()) {
          replaced |= replaceArtifactId(dependency.getXmlTag());
          for (var exclusion : dependency.getExclusions().getExclusions()) {
            replaced |= replaceArtifactId(exclusion.getXmlTag());
          }
        }
        return replaced;
      }

      private void processModule(Module module, Predicate<MavenDomProjectModel> artifactIdReplacer) {
        if (!myProjectsManager.isMavenizedModule(module)) return;
        var mavenProject = myProjectsManager.findProject(module);
        if (null == mavenProject) return;
        var mavenModel = MavenDomUtil.getMavenDomProjectModel(myProject, mavenProject.getFile());
        if (null == mavenModel) return;
        var psiFile = DomUtil.getFile(mavenModel);
        var mavenProjectUpdated = new AtomicBoolean(false);

        WriteCommandAction.writeCommandAction(myProject, psiFile).run(() -> {
          PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
          Document document = documentManager.getDocument(psiFile);
          if (document != null) {
            documentManager.commitDocument(document);
          }

          mavenProjectUpdated.set(artifactIdReplacer.test(mavenModel));

          if (document != null) {
            FileDocumentManager.getInstance().saveDocument(document);
          }
        });
        if (mavenProjectUpdated.get()) {
          myUpdatedMavenProjects.add(mavenProject);
        }
      }

      public void handleModuleRename() {
        if (!myProjectsManager.isMavenizedModule(myModule)) return;
        var mavenProject = myProjectsManager.findProject(myModule);
        if (null == mavenProject) return;
        myGroupId = mavenProject.getMavenId().getGroupId();

        var modules = ModuleManager.getInstance(myProject).getModules();
        for (var module : modules) {
          if (module == myModule) {
            processModule(module, this::replaceModuleArtifactId);
          } else {
            processModule(module, this::replaceArtifactIdReferences);
          }
        }

        myProjectsManager.forceUpdateProjects(myUpdatedMavenProjects);
      }
    }
  }

}
