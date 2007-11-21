package com.intellij.openapi.components.impl.stores;

import com.intellij.ide.impl.convert.ProjectConversionHelper;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.impl.convertors.Convertor01;
import com.intellij.openapi.project.impl.convertors.Convertor12;
import com.intellij.openapi.project.impl.convertors.Convertor23;
import com.intellij.openapi.project.impl.convertors.Convertor34;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.fs.IFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * @author mike
 */
public class IdeaProjectStoreImpl extends ProjectStoreImpl {
  public IdeaProjectStoreImpl(final ProjectEx project) {
    super(project);
  }

  protected void doReload(final Set<Pair<VirtualFile, StateStorage>> changedFiles, @NotNull final Set<String> componentNames) throws StateStorage.StateStorageException {
    super.doReload(changedFiles, componentNames);

    for (Module module : getPersistentModules()) {
      ((ComponentStoreImpl)((ModuleImpl)module).getStateStore()).doReload(changedFiles, componentNames);
    }
  }

  protected void reinitComponents(final Set<String> componentNames) {
    super.reinitComponents(componentNames);

    for (Module module : getPersistentModules()) {
      ((ComponentStoreImpl)((ModuleImpl)module).getStateStore()).reinitComponents(componentNames);
    }
  }

  protected boolean isReloadPossible(final Set<String> componentNames) {
    if (!super.isReloadPossible(componentNames)) return false;

    for (Module module : getPersistentModules()) {
      if (!((ComponentStoreImpl)((ModuleImpl)module).getStateStore()).isReloadPossible(componentNames)) return false;
    }

    return true;
  }

  private Module[] getPersistentModules() {
    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    if (moduleManager instanceof ModuleManagerImpl) {
      return ((ModuleManagerImpl)moduleManager).getPersistentModules();
    }
    else if (moduleManager == null) {
      return Module.EMPTY_ARRAY;
    }
    else {
      return moduleManager.getModules();
    }
  }

  protected SaveSessionImpl createSaveSession() throws StateStorage.StateStorageException {
    return new IdeaProjectSaveSession();
  }

  protected StateStorageManager createStateStorageManager() {
    return new ProjectStateStorageManager(PathMacroManager.getInstance(getComponentManager()).createTrackingSubstitutor(), myProject) {
      public XmlElementStorage.StorageData createWsStorageData() {
        return new IdeaWsStorageData(ROOT_TAG_NAME, myProject);
      }

      public XmlElementStorage.StorageData createIprStorageData() {
        return new IdeaIprStorageData(ROOT_TAG_NAME, myProject);
      }
    };
  }

  @Nullable
  private static ProjectConversionHelper getConversionHelper(Project project) {
    return (ProjectConversionHelper)project.getPicoContainer().getComponentInstance(ProjectConversionHelper.class);
  }

  private class IdeaProjectSaveSession extends ProjectSaveSession {
    List<SaveSession> myModuleSaveSessions = new ArrayList<SaveSession>();

    public IdeaProjectSaveSession() throws StateStorage.StateStorageException {
      try {
        for (Module module : getPersistentModules()) {
          myModuleSaveSessions.add(((ModuleImpl)module).getStateStore().startSave());
        }
      }
      catch (IOException e) {
        throw new StateStorage.StateStorageException(e.getMessage());
      }
    }

    public Collection<String> getUsedMacros() throws StateStorage.StateStorageException {
      Set<String> result = new HashSet<String>(super.getUsedMacros());

      for (SaveSession moduleSaveSession : myModuleSaveSessions) {
        result.addAll(moduleSaveSession.getUsedMacros());
      }

      return result;
    }


    public List<IFile> getAllStorageFiles(final boolean includingSubStructures) {
      final List<IFile> result = super.getAllStorageFiles(includingSubStructures);

      if (includingSubStructures) {
        for (SaveSession moduleSaveSession : myModuleSaveSessions) {
          result.addAll(moduleSaveSession.getAllStorageFiles(true));
        }
      }

      return result;
    }

    @Nullable
    public Set<String> analyzeExternalChanges(final Set<Pair<VirtualFile,StateStorage>> changedFiles) {
      final Set<String> result = super.analyzeExternalChanges(changedFiles);
      if (result == null) return null;

      for (SaveSession moduleSaveSession : myModuleSaveSessions) {
        final Set<String> s = moduleSaveSession.analyzeExternalChanges(changedFiles);
        if (s == null) return null;
        result.addAll(s);
      }

      return result;
    }

    public void finishSave() {
      for (SaveSession moduleSaveSession : myModuleSaveSessions) {
        moduleSaveSession.finishSave();
      }

      super.finishSave();
    }

    protected void beforeSave() throws IOException {
      super.beforeSave();
      for (SaveSession moduleSaveSession : myModuleSaveSessions) {
        moduleSaveSession.save();
      }
    }

    protected void collectSubfilesToSave(final List<IFile> result) throws IOException {
      for (SaveSession moduleSaveSession : myModuleSaveSessions) {
        final List<IFile> moduleFiles = moduleSaveSession.getAllStorageFilesToSave(true);
        result.addAll(moduleFiles);
      }
    }
  }

  private class IdeaWsStorageData extends WsStorageData {
    public IdeaWsStorageData(final String rootElementName, final Project project) {
      super(rootElementName, project);
    }

    public IdeaWsStorageData(final WsStorageData storageData) {
      super(storageData);
    }

    public XmlElementStorage.StorageData clone() {
      return new IdeaWsStorageData(this);
    }

    protected void load(@NotNull final Element rootElement) throws IOException {
      final ProjectConversionHelper conversionHelper = getConversionHelper(myProject);

      if (conversionHelper != null) {
        conversionHelper.convertWorkspaceRootToNewFormat(rootElement);
      }

      super.load(rootElement);
    }

    @NotNull
    protected Element save() {
      final Element result = super.save();

      final ProjectConversionHelper conversionHelper = getConversionHelper(myProject);

      if (conversionHelper != null) {
        conversionHelper.convertWorkspaceRootToOldFormat(result);
      }

      return result;
    }
  }

  private class IdeaIprStorageData extends IprStorageData {

    public IdeaIprStorageData(final String rootElementName, Project project) {
      super(rootElementName, project);
    }

    public IdeaIprStorageData(final IprStorageData storageData) {
      super(storageData);
    }

    public XmlElementStorage.StorageData clone() {
      return new IdeaIprStorageData(this);
    }

    protected void convert(final Element root, final int originalVersion) {
      if (originalVersion < 1) {
        Convertor01.execute(root);
      }
      if (originalVersion < 2) {
        Convertor12.execute(root);
      }
      if (originalVersion < 3) {
        Convertor23.execute(root);
      }
      if (originalVersion < 4) {
        Convertor34.execute(root, myFilePath, getConversionProblemsStorage());
      }
    }

  }
}
