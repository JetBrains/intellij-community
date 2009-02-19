package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectLibrariesConfigurable;

import java.util.HashSet;
import java.util.Set;

public class ProjectLibrariesProvider {
  private final Project myProject;
  private LibrariesModifiableModel myModifiableModel;
  private final Set<Library> myUsedLibraries = new HashSet<Library>();

  public ProjectLibrariesProvider(Project project) {
    myProject = project;

    // todo hack to support addition of modules from GUI
    ProjectLibrariesConfigurable configurable = ProjectLibrariesConfigurable.getInstance(myProject);
    LibraryTableModifiableModelProvider modelProvider = configurable != null ? configurable.getModelProvider(true) : null;
    if (modelProvider != null) {
      myModifiableModel = (LibrariesModifiableModel)modelProvider.getModifiableModel();
    }
  }

  public Library getLibraryByName(String name) {
    return myModifiableModel != null
           ? myModifiableModel.getLibraryByName(name)
           : getLibraryTable().getLibraryByName(name);
  }

  public Library createLibrary(String name) {
    return myModifiableModel != null
           ? myModifiableModel.createLibrary(name)
           : getLibraryTable().createLibrary(name);
  }

  public Library.ModifiableModel getModifiableModel(Library library) {
    return myModifiableModel != null
           ? myModifiableModel.getLibraryModifiableModel(library)
           : library.getModifiableModel();
  }

  public Library[] getAllLibraries() {
    return myModifiableModel != null
           ? myModifiableModel.getLibraries()
           : getLibraryTable().getLibraries();
  }

  public void markLibraryAsUsed(Library library) {
    myUsedLibraries.add(library);
  }

  public Set<Library> getUsedLibraries() {
    return myUsedLibraries;
  }

  public void commit(Library.ModifiableModel libraryModel) {
    if (myModifiableModel == null) libraryModel.commit();
  }

  private LibraryTable getLibraryTable() {
    return ProjectLibraryTable.getInstance(myProject);
  }

  public void removeLibrary(Library library) {
    if (myModifiableModel != null) {
      myModifiableModel.removeLibrary(library);
    }
    else {
      getLibraryTable().removeLibrary(library);
    }
  }
}
