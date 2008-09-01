/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.Facet;
import com.intellij.facet.ui.FacetConfigurationQuickFix;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.facet.ui.libraries.*;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * @author nik
 */
public class FacetLibrariesValidatorImpl extends FacetLibrariesValidator {
  private LibrariesValidatorContext myContext;
  private final FacetValidatorsManager myValidatorsManager;
  private RequiredLibrariesInfo myRequiredLibraries;
  private FacetLibrariesValidatorDescription myDescription;
  private LibraryCompositionSettings myLibraryCompositionSettings;

  public FacetLibrariesValidatorImpl(LibraryInfo[] requiredLibraries, FacetLibrariesValidatorDescription description,
                                     final LibrariesValidatorContext context, FacetValidatorsManager validatorsManager) {
    myContext = context;
    myValidatorsManager = validatorsManager;
    myRequiredLibraries = new RequiredLibrariesInfo(requiredLibraries);
    myDescription = description;
  }

  public void setRequiredLibraries(final LibraryInfo[] requiredLibraries) {
    myRequiredLibraries = new RequiredLibrariesInfo(requiredLibraries);
    myLibraryCompositionSettings = null;
    onChange();
  }

  public boolean isLibrariesAdded() {
    return myLibraryCompositionSettings != null &&
           (!myLibraryCompositionSettings.getUsedLibraries().isEmpty() || !myLibraryCompositionSettings.getAddedJars().isEmpty());
  }

  public void setDescription(@NotNull final FacetLibrariesValidatorDescription description) {
    myDescription = description;
    myLibraryCompositionSettings = null;
    onChange();
  }

  public ValidationResult check() {
    if (myRequiredLibraries == null) {
      return ValidationResult.OK;
    }

    ModuleRootModel rootModel = myContext.getRootModel();
    List<VirtualFile> usedLibraries = collectRoots(rootModel);
    RequiredLibrariesInfo.RequiredClassesNotFoundInfo info = myRequiredLibraries.checkLibraries(usedLibraries.toArray(new VirtualFile[usedLibraries.size()]));
    if (info == null) {
      return ValidationResult.OK;
    }

    String missingJars = IdeBundle.message("label.missed.libraries.prefix") + " " + info.getMissingJarsText();
    final String text = IdeBundle.message("label.missed.libraries.text", missingJars, info.getClassNames()[0]);
    LibraryInfo[] missingLibraries = info.getLibraryInfos();
    final String baseDir = myContext.getProject().getBaseDir().getPath();
    Set<VirtualFile> addedJars = null;
    if (myLibraryCompositionSettings != null) {
      addedJars = myLibraryCompositionSettings.getAddedJars();
    }
    myLibraryCompositionSettings = new LibraryCompositionSettings(missingLibraries, myDescription.getDefaultLibraryName(), baseDir, myDescription.getDefaultLibraryName(), null);
    if (addedJars != null) {
      myLibraryCompositionSettings.setAddedJars(new ArrayList<VirtualFile>(addedJars));
    }
    return new ValidationResult(text, new LibrariesQuickFix(this, myLibraryCompositionSettings, myContext.getLibrariesContainer()));
  }

  private void onChange() {
    myValidatorsManager.validate();
  }

  @Nullable
  public LibraryCompositionSettings getLibraryCompositionSettings() {
    return myLibraryCompositionSettings;
  }

  public void onFacetInitialized(Facet facet) {
    List<Library> addedLibraries = setupLibraries(facet.getModule());

    for (Library addedLibrary : addedLibraries) {
      myDescription.onLibraryAdded(facet, addedLibrary);
    }
  }

  public List<Library> setupLibraries(final Module module) {
    List<Library> addedLibraries = new ArrayList<Library>();
    if (myLibraryCompositionSettings == null) return addedLibraries;

    ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    myLibraryCompositionSettings.addLibraries(model, addedLibraries, myContext.getLibrariesContainer());
    if (model.isChanged()) {
      model.commit();
    }
    else {
      model.dispose();
    }
    return addedLibraries;
  }

  private List<VirtualFile> collectRoots(final @Nullable ModuleRootModel rootModel) {
    ArrayList<VirtualFile> roots = new ArrayList<VirtualFile>();
    if (myLibraryCompositionSettings != null) {
      roots.addAll(myLibraryCompositionSettings.getAddedJars());
      for (Library library : myLibraryCompositionSettings.getUsedLibraries()) {
        roots.addAll(Arrays.asList(myContext.getLibrariesContainer().getLibraryFiles(library, OrderRootType.CLASSES)));
      }
    }
    if (rootModel != null) {
      RootPolicy<List<VirtualFile>> policy = new CollectingLibrariesPolicy();
      rootModel.processOrder(policy, roots);
    }
    return roots;
  }

  private class CollectingLibrariesPolicy extends RootPolicy<List<VirtualFile>> {
    private Set<Module> myProcessedModules = new HashSet<Module>();

    public List<VirtualFile> visitLibraryOrderEntry(final LibraryOrderEntry libraryOrderEntry, final List<VirtualFile> value) {
      Library library = libraryOrderEntry.getLibrary();
      if (library != null) {
        value.addAll(Arrays.asList(myContext.getLibrariesContainer().getLibraryFiles(library, OrderRootType.CLASSES)));
      }
      return value;
    }

    public List<VirtualFile> visitModuleOrderEntry(final ModuleOrderEntry moduleOrderEntry, final List<VirtualFile> value) {
      Module module = moduleOrderEntry.getModule();
      if (module != null && myProcessedModules.add(module)) {
        ModuleRootModel dependency = myContext.getModulesProvider().getRootModel(module);
        if (dependency != null) {
          return dependency.processOrder(this, value);
        }
      }
      return value;
    }

  }

  private static class LibrariesQuickFix extends FacetConfigurationQuickFix {
    private final FacetLibrariesValidatorImpl myValidator;
    private final LibraryCompositionSettings myLibrarySettings;
    private LibrariesContainer myLibrariesContainer;

    public LibrariesQuickFix(FacetLibrariesValidatorImpl validator, final LibraryCompositionSettings libraryCompositionSettings,
                             final LibrariesContainer librariesContainer) {
      super(IdeBundle.message("missing.libraries.fix.button"));
      myValidator = validator;
      myLibrarySettings = libraryCompositionSettings;
      myLibrariesContainer = librariesContainer;
    }

    public void run(final JComponent place) {
      LibraryDownloadingMirrorsMap mirrorsMap = new LibraryDownloadingMirrorsMap();
      for (LibraryInfo libraryInfo : myLibrarySettings.getLibraryInfos()) {
        LibraryDownloadInfo downloadingInfo = libraryInfo.getDownloadingInfo();
        if (downloadingInfo != null) {
          RemoteRepositoryInfo repositoryInfo = downloadingInfo.getRemoteRepository();
          if (repositoryInfo != null) {
            mirrorsMap.registerRepository(repositoryInfo);
          }
        }
      }
      LibraryCompositionOptionsPanel panel = new LibraryCompositionOptionsPanel(myLibrariesContainer, myLibrarySettings, mirrorsMap);
      LibraryCompositionDialog dialog = new LibraryCompositionDialog(place, panel, myLibrariesContainer, mirrorsMap);
      dialog.show();
      myValidator.onChange();
    }
  }

  private static class LibraryCompositionDialog extends DialogWrapper {
    private LibraryCompositionOptionsPanel myPanel;
    private final LibraryDownloadingMirrorsMap myMirrorsMap;
    private final LibrariesContainer myLibrariesContainer;

    private LibraryCompositionDialog(final JComponent parent, final LibraryCompositionOptionsPanel panel,
                                     final LibrariesContainer librariesContainer, final LibraryDownloadingMirrorsMap mirrorsMap) {
      super(parent, true);
      myLibrariesContainer = librariesContainer;
      setTitle(IdeBundle.message("specify.libraries.dialog.title"));
      myPanel = panel;
      myMirrorsMap = mirrorsMap;
      init();
    }

    protected JComponent createCenterPanel() {
      return myPanel.getMainPanel();
    }

    protected void doOKAction() {
      myPanel.apply();
      if (myPanel.getLibraryCompositionSettings().downloadFiles(myMirrorsMap, myLibrariesContainer, myPanel.getMainPanel())) {
        super.doOKAction();
      }
    }
  }
}
