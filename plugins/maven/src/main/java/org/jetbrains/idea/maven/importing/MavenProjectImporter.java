/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.importing;

import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.Stack;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenProjectImporter {
  private final Project myProject;
  private final MavenProjectsTree myProjectsTree;
  private final Map<VirtualFile, Module> myFileToModuleMapping;
  private volatile Map<MavenProject, MavenProjectChanges> myProjectsToImportWithChanges;
  private volatile Set<MavenProject> myAllProjects;
  private final boolean myImportModuleGroupsRequired;
  private final MavenModifiableModelsProvider myModelsProvider;
  private final MavenImportingSettings myImportingSettings;

  private final ModifiableModuleModel myModuleModel;

  private final List<Module> myCreatedModules = new ArrayList<Module>();

  private final Map<MavenProject, Module> myMavenProjectToModule = new THashMap<MavenProject, Module>();
  private final Map<MavenProject, String> myMavenProjectToModuleName = new THashMap<MavenProject, String>();
  private final Map<MavenProject, String> myMavenProjectToModulePath = new THashMap<MavenProject, String>();

  public MavenProjectImporter(Project p,
                              MavenProjectsTree projectsTree,
                              Map<VirtualFile, Module> fileToModuleMapping,
                              Map<MavenProject, MavenProjectChanges> projectsToImportWithChanges,
                              boolean importModuleGroupsRequired,
                              MavenModifiableModelsProvider modelsProvider,
                              MavenImportingSettings importingSettings) {
    myProject = p;
    myProjectsTree = projectsTree;
    myFileToModuleMapping = fileToModuleMapping;
    myProjectsToImportWithChanges = projectsToImportWithChanges;
    myImportModuleGroupsRequired = importModuleGroupsRequired;
    myModelsProvider = modelsProvider;
    myImportingSettings = importingSettings;

    myModuleModel = modelsProvider.getModuleModel();
  }

  public List<MavenProjectsProcessorTask> importProject() {
    List<MavenProjectsProcessorTask> postTasks = new ArrayList<MavenProjectsProcessorTask>();

    boolean hasChanges = false;

    // in the case projects are changed during importing we must memorise them
    myAllProjects = new LinkedHashSet<MavenProject>(myProjectsTree.getProjects());
    myAllProjects.addAll(myProjectsToImportWithChanges.keySet()); // some projects may already have been removed from the tree

    hasChanges |= deleteIncompatibleModules();
    myProjectsToImportWithChanges = collectProjectsToImport(myProjectsToImportWithChanges);

    mapMavenProjectsToModulesAndNames();

    boolean projectsHaveChanges = projectsToImportHaveChanges();
    if (projectsHaveChanges) {
      hasChanges = true;
      importModules(postTasks);
      scheduleRefreshResolvedArtifacts(postTasks);
      configSettings();
    }

    if (projectsHaveChanges || myImportModuleGroupsRequired) {
      hasChanges = true;
      configModuleGroups();
    }

    boolean modulesDeleted = deleteObsoleteModules();
    hasChanges |= modulesDeleted;
    if (hasChanges) {
      removeUnusedProjectLibraries();
    }

    if (hasChanges) {
      myModelsProvider.commit();
    }
    else {
      myModelsProvider.dispose();
    }

    return postTasks;
  }

  private boolean projectsToImportHaveChanges() {
    for (MavenProjectChanges each : myProjectsToImportWithChanges.values()) {
      if (each.hasChanges()) return true;
    }
    return false;
  }

  private Map<MavenProject, MavenProjectChanges> collectProjectsToImport(Map<MavenProject, MavenProjectChanges> projectsToImport) {
    Map<MavenProject, MavenProjectChanges> result = new THashMap<MavenProject, MavenProjectChanges>(projectsToImport);
    result.putAll(collectNewlyCreatedProjects()); // e.g. when 'create modules fro aggregators' setting changes

    Set<MavenProject> allProjectsToImport = result.keySet();
    Set<MavenProject> selectedProjectsToImport = selectProjectsToImport(allProjectsToImport);

    Iterator<MavenProject> it = allProjectsToImport.iterator();
    while (it.hasNext()) {
      if (!selectedProjectsToImport.contains(it.next())) it.remove();
    }

    return result;
  }

  private Map<MavenProject, MavenProjectChanges> collectNewlyCreatedProjects() {
    Map<MavenProject, MavenProjectChanges> result = new THashMap<MavenProject, MavenProjectChanges>();

    for (MavenProject each : myAllProjects) {
      Module module = myFileToModuleMapping.get(each.getFile());
      if (module == null) {
        result.put(each, MavenProjectChanges.ALL);
      }
    }

    return result;
  }

  private Set<MavenProject> selectProjectsToImport(Collection<MavenProject> originalProjects) {
    Set<MavenProject> result = new THashSet<MavenProject>();
    for (MavenProject each : originalProjects) {
      if (!shouldCreateModuleFor(each)) continue;
      result.add(each);
    }
    return result;
  }

  private boolean shouldCreateModuleFor(MavenProject project) {
    if (myProjectsTree.isIgnored(project)) return false;
    return !project.isAggregator() || myImportingSettings.isCreateModulesForAggregators();
  }

  private boolean deleteIncompatibleModules() {
    final List<Pair<MavenProject, Module>> incompatible = collectIncompatibleModulesWithProjects();
    if (incompatible.isEmpty()) return false;

    final int[] result = new int[1];
    MavenUtil.invokeAndWait(myProject, myModelsProvider.getModalityStateForQuestionDialogs(), new Runnable() {
      public void run() {
        String message = ProjectBundle.message("maven.import.incompatible.modules",
                                               formatProjectsWithModules(incompatible),
                                               incompatible.size() == 1 ? "" : "s");
        String[] options = {
          ProjectBundle.message("maven.import.incompatible.modules.recreate"),
          ProjectBundle.message("maven.import.incompatible.modules.ignore")
        };

        result[0] = Messages.showDialog(myProject, message,
                                        ProjectBundle.message("maven.tab.importing"),
                                        options, 0, Messages.getQuestionIcon());
      }
    });

    if (result[0] == 0) {
      for (Pair<MavenProject, Module> each : incompatible) {
        myFileToModuleMapping.remove(each.first.getFile());
        myModuleModel.disposeModule(each.second);
      }
      return true;
    }
    else {
      myProjectsTree.setIgnoredState(MavenUtil.collectFirsts(incompatible), true, this);
      return false;
    }
  }

  private List<Pair<MavenProject, Module>> collectIncompatibleModulesWithProjects() {
    List<Pair<MavenProject, Module>> incompatible = new ArrayList<Pair<MavenProject, Module>>();
    for (MavenProject each : myAllProjects) {
      Module module = myFileToModuleMapping.get(each.getFile());
      if (module == null) continue;

      if (shouldCreateModuleFor(each) && !(module.getModuleType() instanceof JavaModuleType)) {
        incompatible.add(Pair.create(each, module));
      }
    }
    return incompatible;
  }

  private static String formatProjectsWithModules(List<Pair<MavenProject, Module>> projectsWithModules) {
    return StringUtil.join(projectsWithModules, new Function<Pair<MavenProject, Module>, String>() {
      public String fun(Pair<MavenProject, Module> each) {
        MavenProject project = each.first;
        Module module = each.second;
        return module.getModuleType().getName() +
               " '" +
               module.getName() +
               "' for Maven project '" +
               project.getMavenId().getDisplayString() +
               "'";
      }
    }, "<br>");
  }

  private boolean deleteObsoleteModules() {
    final List<Module> obsoleteModules = collectObsoleteModules();
    if (obsoleteModules.isEmpty()) return false;

    setMavenizedModules(obsoleteModules, false);

    final int[] result = new int[1];
    MavenUtil.invokeAndWait(myProject, myModelsProvider.getModalityStateForQuestionDialogs(), new Runnable() {
      public void run() {
        result[0] = Messages.showYesNoDialog(myProject,
                                             ProjectBundle.message("maven.import.message.delete.obsolete", formatModules(obsoleteModules)),
                                             ProjectBundle.message("maven.tab.importing"),
                                             Messages.getQuestionIcon());
      }
    });

    if (result[0] == 1) return false;// NO

    for (Module each : obsoleteModules) {
      myModuleModel.disposeModule(each);
    }

    return true;
  }

  private List<Module> collectObsoleteModules() {
    List<Module> remainingModules = new ArrayList<Module>();
    Collections.addAll(remainingModules, myModuleModel.getModules());

    for (MavenProject each : selectProjectsToImport(myAllProjects)) {
      remainingModules.remove(myMavenProjectToModule.get(each));
    }

    List<Module> obsolete = new ArrayList<Module>();
    for (Module each : remainingModules) {
      if (MavenProjectsManager.getInstance(myProject).isMavenizedModule(each)) {
        obsolete.add(each);
      }
    }
    return obsolete;
  }

  private static String formatModules(final Collection<Module> modules) {
    return StringUtil.join(modules, new Function<Module, String>() {
      public String fun(final Module m) {
        return "'" + m.getName() + "'";
      }
    }, "\n");
  }

  private void scheduleRefreshResolvedArtifacts(List<MavenProjectsProcessorTask> postTasks) {
    // We have to refresh all the resolved artifacts manually in order to
    // update all the VirtualFilePointers. It is not enough to call
    // VirtualFileManager.refresh() since the newly created files will be only
    // picked by FS when FileWathcer finishes its work. And in the case of import
    // it doesn't finish in time.
    // I couldn't manage to write a test for this since behaviour of VirtualFileManager
    // and FileWatcher differs from real-life execution.

    List<MavenArtifact> artifacts = new ArrayList<MavenArtifact>();
    for (MavenProject each : myProjectsToImportWithChanges.keySet()) {
      artifacts.addAll(each.getDependencies());
    }

    final Set<File> files = new THashSet<File>();
    for (MavenArtifact each : artifacts) {
      if (each.isResolved()) files.add(each.getFile());
    }

    final Runnable r = new Runnable() {
      public void run() {
        LocalFileSystem.getInstance().refreshIoFiles(files);
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      r.run();
    }
    else {
      postTasks.add(new MavenProjectsProcessorTask() {
        public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
          throws MavenProcessCanceledException {
          indicator.setText("Refreshing files...");
          r.run();
        }
      });
    }
  }

  private void mapMavenProjectsToModulesAndNames() {
    for (MavenProject each : myAllProjects) {
      Module module = myFileToModuleMapping.get(each.getFile());
      if (module != null) {
        myMavenProjectToModule.put(each, module);
      }
    }

    MavenModuleNameMapper.map(myAllProjects,
                              myMavenProjectToModule,
                              myMavenProjectToModuleName,
                              myMavenProjectToModulePath,
                              myImportingSettings.getDedicatedModuleDir());
  }

  private void configSettings() {
    MavenUtil.invokeAndWaitWriteAction(myProject, new Runnable() {
      public void run() {
        String level = calcTargetLevel();
        if (level == null) return;

        String options = JavacSettings.getInstance(myProject).ADDITIONAL_OPTIONS_STRING;
        Pattern pattern = Pattern.compile("(-target (\\S+))");
        Matcher matcher = pattern.matcher(options);

        if (matcher.find()) {
          String currentValue = MavenProject.normalizeCompilerLevel(matcher.group(2));

          if (currentValue == null || compareCompilerLevel(level, currentValue) < 0) {
            StringBuffer buffer = new StringBuffer();
            matcher.appendReplacement(buffer, "-target " + level);
            matcher.appendTail(buffer);
            options = buffer.toString();
          }
        }
        else {
          if (!StringUtil.isEmptyOrSpaces(options)) options += " ";
          options += "-target " + level;
        }
        JavacSettings.getInstance(myProject).ADDITIONAL_OPTIONS_STRING = options;
      }
    });
  }

  private String calcTargetLevel() {
    String maxSource = null;
    String minTarget = null;
    for (MavenProject each : myAllProjects) {
      String source = each.getSourceLevel();
      String target = each.getTargetLevel();
      if (source != null && (maxSource == null || compareCompilerLevel(maxSource, source) < 0)) maxSource = source;
      if (target != null && (minTarget == null || compareCompilerLevel(minTarget, target) > 0)) minTarget = target;
    }
    return (maxSource != null && compareCompilerLevel(minTarget, maxSource) < 0) ? maxSource : minTarget;
  }

  private int compareCompilerLevel(String left, String right) {
    if (left == null && right == null) return 0;
    if (left == null) return -1;
    if (right == null) return 1;
    return left.compareTo(right);
  }

  private void importModules(final List<MavenProjectsProcessorTask> postTasks) {
    Map<MavenProject, MavenProjectChanges> projectsWithChanges = myProjectsToImportWithChanges;
    Set<MavenProject> projects = projectsWithChanges.keySet();

    Set<MavenProject> projectsWithNewlyCreatedModules = new THashSet<MavenProject>();

    for (MavenProject each : projects) {
      if (ensureModuleCreated(each)) {
        projectsWithNewlyCreatedModules.add(each);
      }
    }

    final Map<Module, MavenModuleImporter> moduleImporters = new THashMap<Module, MavenModuleImporter>();
    for (Map.Entry<MavenProject, MavenProjectChanges> each : projectsWithChanges.entrySet()) {
      MavenProject project = each.getKey();
      Module module = myMavenProjectToModule.get(project);
      boolean isNewModule = projectsWithNewlyCreatedModules.contains(project);

      MavenModuleImporter moduleImporter = createModuleImporter(module, Pair.create(project, each.getValue()));
      moduleImporters.put(module, moduleImporter);

      moduleImporter.config(isNewModule);
    }

    for (MavenProject each : projects) {
      moduleImporters.get(myMavenProjectToModule.get(each)).preConfigFacets();
    }
    for (MavenProject each : projects) {
      moduleImporters.get(myMavenProjectToModule.get(each)).configFacets(postTasks);
    }

    setMavenizedModules(moduleImporters.keySet(), true);
  }

  private void setMavenizedModules(final Collection<Module> modules, final boolean mavenized) {
    MavenUtil.invokeAndWaitWriteAction(myProject, new Runnable() {
      public void run() {
        MavenProjectsManager.getInstance(myProject).setMavenizedModules(modules, mavenized);
      }
    });
  }

  private boolean ensureModuleCreated(MavenProject project) {
    if (myMavenProjectToModule.get(project) != null) return false;

    final String path = myMavenProjectToModulePath.get(project);

    // for some reason newModule opens the existing iml file, so we
    // have to remove it beforehand.
    deleteExistingImlFile(path);

    final Module module = myModuleModel.newModule(path, StdModuleTypes.JAVA);
    myMavenProjectToModule.put(project, module);
    myCreatedModules.add(module);
    return true;
  }

  private void deleteExistingImlFile(final String path) {
    MavenUtil.invokeAndWaitWriteAction(myProject, new Runnable() {
      public void run() {
        try {
          VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
          if (file != null) file.delete(this);
        }
        catch (IOException e) {
          MavenLog.LOG.warn("Cannot delete existing iml file: " + path, e);
        }
      }
    });
  }

  private MavenModuleImporter createModuleImporter(Module module, Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
    return new MavenModuleImporter(module,
                                   myProjectsTree,
                                   projectWithChanges,
                                   myMavenProjectToModuleName,
                                   myImportingSettings,
                                   myModelsProvider);
  }

  private void configModuleGroups() {
    if (!myImportingSettings.isCreateModuleGroups()) return;

    final Stack<String> groups = new Stack<String>();
    final boolean createTopLevelGroup = myProjectsTree.getRootProjects().size() > 1;

    myProjectsTree.visit(new MavenProjectsTree.SimpleVisitor() {
      int depth = 0;

      @Override
      public boolean shouldVisit(MavenProject project) {
        // in case some project has been added while we were importing
        return myMavenProjectToModuleName.containsKey(project);
      }

      public void visit(MavenProject each) {
        depth++;

        String name = myMavenProjectToModuleName.get(each);

        if (shouldCreateGroup(each)) {
          groups.push(ProjectBundle.message("module.group.name", name));
        }

        if (!shouldCreateModuleFor(each)) {
          return;
        }

        Module module = myModuleModel.findModuleByName(name);
        if (module == null) return;
        myModuleModel.setModuleGroupPath(module, groups.isEmpty() ? null : ArrayUtil.toStringArray(groups));
      }

      public void leave(MavenProject each) {
        if (shouldCreateGroup(each)) {
          groups.pop();
        }
        depth--;
      }

      private boolean shouldCreateGroup(MavenProject project) {
        return !myProjectsTree.getModules(project).isEmpty()
               && (createTopLevelGroup || depth > 1);
      }
    });
  }

  private boolean removeUnusedProjectLibraries() {
    Set<Library> allLibraries = new THashSet<Library>();
    Collections.addAll(allLibraries, myModelsProvider.getAllLibraries());

    Set<Library> usedLibraries = new THashSet<Library>();
    for (ModuleRootModel eachModel : collectModuleModels()) {
      for (OrderEntry eachEntry : eachModel.getOrderEntries()) {
        if (eachEntry instanceof LibraryOrderEntry) {
          Library lib = ((LibraryOrderEntry)eachEntry).getLibrary();
          if (MavenRootModelAdapter.isMavenLibrary(lib)) usedLibraries.add(lib);
        }
      }
    }

    Set<Library> unusedLibraries = new THashSet<Library>(allLibraries);
    unusedLibraries.removeAll(usedLibraries);

    boolean removed = false;
    for (Library each : unusedLibraries) {
      if (MavenRootModelAdapter.isMavenLibrary(each) && !MavenRootModelAdapter.isChangedByUser(each)) {
        myModelsProvider.removeLibrary(each);
        removed = true;
      }
    }
    return removed;
  }

  private Collection<ModuleRootModel> collectModuleModels() {
    Map<Module, ModuleRootModel> rootModels = new THashMap<Module, ModuleRootModel>();
    for (MavenProject each : myProjectsToImportWithChanges.keySet()) {
      Module module = myMavenProjectToModule.get(each);
      ModifiableRootModel rootModel = myModelsProvider.getRootModel(module);
      rootModels.put(module, rootModel);
    }
    for (Module each : myModuleModel.getModules()) {
      if (rootModels.containsKey(each)) continue;
      rootModels.put(each, myModelsProvider.getRootModel(each));
    }
    return rootModels.values();
  }

  public List<Module> getCreatedModules() {
    return myCreatedModules;
  }
}
