/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.Facet;
import com.intellij.facet.ui.FacetConfigurationQuickFix;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.facet.ui.libraries.FacetLibrariesValidator;
import com.intellij.facet.ui.libraries.FacetLibrariesValidatorDescription;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class FacetLibrariesValidatorImpl extends FacetLibrariesValidator {
  private FacetEditorContext myContext;
  private final FacetValidatorsManager myValidatorsManager;
  private RequiredLibrariesInfo myRequiredLibraries;
  private final FacetLibrariesValidatorDescription myDescription;
  private List<VirtualFile> myAddedRoots;
  private List<Library> myAddedLibraries;

  public FacetLibrariesValidatorImpl(LibraryInfo[] requiredLibraries, FacetLibrariesValidatorDescription description,
                                     final FacetEditorContext context, FacetValidatorsManager validatorsManager) {
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

    StringBuilder missedJarsText = new StringBuilder(IdeBundle.message("label.missed.libraries.prefix"));
    missedJarsText.append(' ');
    for (int i = 0; i < info.getLibraryInfos().length; i++) {
      if (i > 0) {
        missedJarsText.append(", ");
      }

      missedJarsText.append(info.getLibraryInfos()[i].getExpectedJarName());
    }
    final String text = IdeBundle.message("label.missed.libraries.text", missedJarsText, info.getClassNames()[0]);
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
          result.setResult(createLibrary(files, project));
        }
      }.execute().getResultObject();
      rootModel.addLibraryEntry(library);
    }
    onChange();
  }

  private Library createLibrary(final VirtualFile[] roots, final Project project) {
    final Library library = myContext.createProjectLibrary(myDescription.getDefaultLibraryName(), roots);
    final Library.ModifiableModel model = library.getModifiableModel();
    for (VirtualFile root : roots) {
      model.addRoot(root, OrderRootType.CLASSES);
    }
    model.commit();
    return library;
  }

  public void onFacetInitialized(Facet facet) {
    Module module = facet.getModule();
    ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();

    List<Library> addedLibraries = new ArrayList<Library>();
    for (Library library : myAddedLibraries) {
      model.addLibraryEntry(library);
      addedLibraries.add(library);
    }

    if (!myAddedRoots.isEmpty()) {
      VirtualFile[] roots = myAddedRoots.toArray(new VirtualFile[myAddedRoots.size()]);
      Library library = createLibrary(roots, module.getProject());
      model.addLibraryEntry(library);
      addedLibraries.add(library);
    }

    if (model.isChanged()) {
      model.commit();
    }
    else {
      model.dispose();
    }

    for (Library addedLibrary : addedLibraries) {
      myDescription.onLibraryAdded(facet, addedLibrary);
    }
  }

  private void downloadJars(LibraryInfo[] missingLibraries, JComponent place) {
    LibraryDownloader downloader = new LibraryDownloader(missingLibraries, null, place);
    VirtualFile[] roots = downloader.download();
    addRoots(roots);
  }

  private List<VirtualFile> collectRoots(final @Nullable ModuleRootModel rootModel) {
    ArrayList<VirtualFile> roots = new ArrayList<VirtualFile>();
    roots.addAll(myAddedRoots);
    for (Library library : myAddedLibraries) {
      roots.addAll(Arrays.asList(library.getFiles(OrderRootType.CLASSES)));
    }
    if (rootModel != null) {
      RootPolicy<List<VirtualFile>> policy = new CollectingLibrariesPolicy();
      rootModel.processOrder(policy, roots);
    }
    return roots;
  }

  private class CollectingLibrariesPolicy extends RootPolicy<List<VirtualFile>> {
    public List<VirtualFile> visitLibraryOrderEntry(final LibraryOrderEntry libraryOrderEntry, final List<VirtualFile> value) {
      value.addAll(Arrays.asList(libraryOrderEntry.getFiles(OrderRootType.CLASSES)));
      return value;
    }

    public List<VirtualFile> visitModuleOrderEntry(final ModuleOrderEntry moduleOrderEntry, final List<VirtualFile> value) {
      Module module = moduleOrderEntry.getModule();
      if (module != null) {
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

        if (missingLibrariesInfo.checkLibraries(library.getFiles(OrderRootType.CLASSES)) == null) {
          suitableLibraries.add(library);
        }
      }

      final Ref<ListPopup> popupRef = Ref.create(null);
      final String useLibrary = IdeBundle.message("facet.libraries.use.library.popup.item");
      final String downloadJars = IdeBundle.message("facet.libraries.download.jars.popup.item");
      final String addJars = IdeBundle.message("facet.libraries.create.new.library.popup.item");
      final BaseListPopupStep librariesStep;
      String[] popupItems;
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
        popupItems = new String[] {useLibrary, downloadJars, addJars};
      }
      else {
        librariesStep = null;
        popupItems = new String[] {downloadJars, addJars};
      }


      final BaseListPopupStep popupStep = new BaseListPopupStep<String>(null, popupItems) {
        public boolean hasSubstep(final String selectedValue) {
          return useLibrary.equals(selectedValue);
        }

        public PopupStep onChosen(final String selectedValue, final boolean finalChoice) {
          if (useLibrary.equals(selectedValue)) {
            return librariesStep;
          }
          popupRef.get().cancel();
          if (downloadJars.equals(selectedValue)) {
            downloadJars(myMissingLibraries, place);
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
