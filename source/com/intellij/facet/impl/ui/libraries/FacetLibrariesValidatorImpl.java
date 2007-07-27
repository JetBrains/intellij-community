/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.Facet;
import com.intellij.facet.impl.ui.FacetEditorContextBase;
import com.intellij.facet.ui.FacetConfigurationQuickFix;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.facet.ui.libraries.FacetLibrariesValidator;
import com.intellij.facet.ui.libraries.FacetLibrariesValidatorDescription;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.facet.ui.libraries.LibraryDownloadInfo;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author nik
 */
public class FacetLibrariesValidatorImpl extends FacetLibrariesValidator {
  private static final Icon QUICK_FIX_ICON = IconLoader.getIcon("/actions/intentionBulb.png");
  private LibrariesValidatorContext myContext;
  private final FacetValidatorsManager myValidatorsManager;
  private RequiredLibrariesInfo myRequiredLibraries;
  private final FacetLibrariesValidatorDescription myDescription;
  private List<VirtualFile> myAddedRoots;
  private List<Library> myAddedLibraries;

  public FacetLibrariesValidatorImpl(LibraryInfo[] requiredLibraries, FacetLibrariesValidatorDescription description,
                                     final LibrariesValidatorContext context, FacetValidatorsManager validatorsManager) {
    myContext = context;
    myValidatorsManager = validatorsManager;
    myRequiredLibraries = new RequiredLibrariesInfo(requiredLibraries);
    myDescription = description;
    myAddedRoots = new ArrayList<VirtualFile>();
    myAddedLibraries = new ArrayList<Library>();
  }

  public void setRequiredLibraries(final LibraryInfo[] requiredLibraries) {
    myRequiredLibraries = new RequiredLibrariesInfo(requiredLibraries);
    onChange();
  }

  public FacetLibrariesValidatorDescription getDescription() {
    return myDescription;
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
    return new ValidationResult(text, new LibrariesQuickFix(info.getLibraryInfos()));
  }

  private void addLibrary(final Library selectedValue) {
    ModifiableRootModel rootModel = myContext.getModifiableRootModel();
    if (rootModel == null) {
      myAddedLibraries.add(selectedValue);
    }
    else {
      rootModel.addLibraryEntry(selectedValue);
    }
    onChange();
  }

  private void onChange() {
    myValidatorsManager.validate();
  }

  private void addJars(JComponent place) {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, false, true, false, false, true);
    descriptor.setTitle(IdeBundle.message("file.chooser.select.paths.title"));
    descriptor.setDescription(IdeBundle.message("file.chooser.multiselect.description"));
    final VirtualFile[] files = FileChooser.chooseFiles(place, descriptor);
    addRoots(files);
  }

  public void addRoots(final VirtualFile... files) {
    if (files.length == 0) return;

    ModifiableRootModel rootModel = myContext.getModifiableRootModel();
    final Project project = myContext.getProject();
    if (rootModel == null || project == null) {
      myAddedRoots.addAll(Arrays.asList(files));
    }
    else {
      Library library = new WriteAction<Library>() {
        protected void run(final Result<Library> result) {
          result.setResult(createLibrary(files));
        }
      }.execute().getResultObject();
      if (library != null) {
        rootModel.addLibraryEntry(library);
      }
    }
    onChange();
  }

  private Library createLibrary(final VirtualFile[] roots) {
    return myContext.createProjectLibrary(myDescription.getDefaultLibraryName(), roots);
  }

  public void onFacetInitialized(Facet facet) {
    List<Library> addedLibraries = setupLibraries(facet.getModule());

    for (Library addedLibrary : addedLibraries) {
      myDescription.onLibraryAdded(facet, addedLibrary);
    }
  }

  public List<Library> setupLibraries(final Module module) {
    ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();

    List<Library> addedLibraries = new ArrayList<Library>();
    for (Library library : myAddedLibraries) {
      model.addLibraryEntry(library);
      addedLibraries.add(library);
    }

    if (!myAddedRoots.isEmpty()) {
      VirtualFile[] roots = myAddedRoots.toArray(new VirtualFile[myAddedRoots.size()]);
      LibraryTable libraryTable = ProjectLibraryTable.getInstance(module.getProject());
      Library library = FacetEditorContextBase.createLibraryInTable(myDescription.getDefaultLibraryName(), roots, VirtualFile.EMPTY_ARRAY, libraryTable);
      model.addLibraryEntry(library);
      addedLibraries.add(library);
    }

    if (model.isChanged()) {
      model.commit();
    }
    else {
      model.dispose();
    }
    return addedLibraries;
  }

  private void downloadJars(final LibraryDownloadInfo[] downloadInfos, JComponent place) {
    LibraryDownloader downloader = new LibraryDownloader(downloadInfos, null, place);
    VirtualFile[] roots = downloader.download();
    addRoots(roots);
  }

  private List<VirtualFile> collectRoots(final @Nullable ModuleRootModel rootModel) {
    ArrayList<VirtualFile> roots = new ArrayList<VirtualFile>();
    roots.addAll(myAddedRoots);
    for (Library library : myAddedLibraries) {
      roots.addAll(Arrays.asList(myContext.getFiles(library, OrderRootType.CLASSES)));
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
        value.addAll(Arrays.asList(myContext.getFiles(library, OrderRootType.CLASSES)));
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

  private class LibrariesQuickFix extends FacetConfigurationQuickFix {
    private final LibraryInfo[] myMissingLibraries;

    public LibrariesQuickFix(final LibraryInfo[] missingLibraries) {
      myMissingLibraries = missingLibraries;
    }

    public void run(final JComponent place) {
      Library[] allLibraries = myContext.getLibraries();
      List<Library> suitableLibraries = new ArrayList<Library>();
      RequiredLibrariesInfo missingLibrariesInfo = new RequiredLibrariesInfo(myMissingLibraries);
      for (Library library : allLibraries) {
        if (myAddedLibraries.contains(library)) continue;

        if (missingLibrariesInfo.checkLibraries(myContext.getFiles(library, OrderRootType.CLASSES)) == null) {
          suitableLibraries.add(library);
        }
      }

      final Ref<ListPopup> popupRef = Ref.create(null);
      final String useLibrary = IdeBundle.message("facet.libraries.use.library.popup.item");
      final String downloadJars = IdeBundle.message("facet.libraries.download.jars.popup.item");
      final String addJars = IdeBundle.message("facet.libraries.create.new.library.popup.item");
      final BaseListPopupStep librariesStep;
      List<String> popupItems = new ArrayList<String>();
      if (!suitableLibraries.isEmpty()) {
        librariesStep = new BaseListPopupStep<Library>(IdeBundle.message("add.library.popup.title"), suitableLibraries, Icons.LIBRARY_ICON) {
          @NotNull
          public String getTextFor(final Library value) {
            return value.getName();
          }

          public PopupStep onChosen(final Library selectedValue, final boolean finalChoice) {
            addLibrary(selectedValue);
            return FINAL_CHOICE;
          }
        };
        popupItems.add(useLibrary);
      }
      else {
        librariesStep = null;
      }

      final LibraryDownloadInfo[] downloadInfos = LibraryDownloader.getDownloadingInfos(myMissingLibraries);
      if (downloadInfos.length > 0) {
        popupItems.add(downloadJars);
      }
      popupItems.add(addJars);

      final BaseListPopupStep popupStep = new BaseListPopupStep<String>(null, popupItems, QUICK_FIX_ICON) {
        public boolean hasSubstep(final String selectedValue) {
          return useLibrary.equals(selectedValue);
        }

        public PopupStep onChosen(final String selectedValue, final boolean finalChoice) {
          if (useLibrary.equals(selectedValue)) {
            return librariesStep;
          }
          popupRef.get().cancel();
          if (downloadJars.equals(selectedValue)) {
            downloadJars(downloadInfos, place);
          }
          else {
            addJars(place);
          }
          return FINAL_CHOICE;
        }
      };
      final ListPopup popup = JBPopupFactory.getInstance().createListPopup(popupStep);
      popupRef.set(popup);
      popup.showUnderneathOf(place);
    }
  }

}
