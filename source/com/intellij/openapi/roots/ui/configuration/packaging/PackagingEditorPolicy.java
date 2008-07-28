package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.deployment.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ChooseModulesDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author nik
 */
public abstract class PackagingEditorPolicy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.packaging.PackagingEditorPolicy");
  public static final String NOT_APPLICABLE = IdeBundle.message("text.not.applicable");
  private final Module myModule;

  protected PackagingEditorPolicy(final Module module) {
    myModule = module;
  }

  protected abstract PackagingMethod[] getAllowedPackagingMethodsForLibrary(LibraryLink libraryLink);

  protected abstract PackagingMethod[] getAllowedPackagingMethodsForModule(@NotNull Module module);

  public abstract void setDefaultAttributes(ContainerElement element);

  public abstract String suggestDefaultRelativePath(ContainerElement element);

  protected abstract List<Module> getSuitableModules(final PackagingEditor packagingEditor);

  protected abstract List<Library> getSuitableLibraries(final PackagingEditor packagingEditor);

  protected abstract ContainerElement[] getModifiedElements(PackagingEditor packagingEditor);

  public boolean isRelativePathCellEditable(final ContainerElement element) {
    return element instanceof LibraryLink
           || element instanceof ModuleLink && ((ModuleLink)element).getModule() != null;
  }

  @Nullable
  public AdditionalPropertiesConfigurable getAdditionalPropertiesConfigurable(@NotNull ContainerElement element) {
    return null;
  }

  public PackagingMethod[] getAllowedPackagingMethods(ContainerElement element) {
    if (element instanceof LibraryLink) {
      final LibraryLink libraryLink = (LibraryLink)element;
      if (libraryLink.getLibrary() == null) {
        return getPackagingMethodForUnresolvedElement(libraryLink);
      }
      return getAllowedPackagingMethodsForLibrary(libraryLink);
    }
    if (element instanceof ModuleLink) {
      final ModuleLink moduleLink = (ModuleLink)element;
      final Module module = moduleLink.getModule();
      if (module != null) {
        return getAllowedPackagingMethodsForModule(module);
      }
      return getPackagingMethodForUnresolvedElement(moduleLink);
    }
    LOG.assertTrue(false, "unexpected element: " + element.getClass());
    return null;
  }

  protected PackagingMethod[] getPackagingMethodForUnresolvedElement(final ContainerElement element) {
    final PackagingMethod method = element.getPackagingMethod();
    if (method == PackagingMethod.DO_NOT_PACKAGE) {
      return PackagingMethod.EMPTY_ARRAY;
    }
    return new PackagingMethod[]{method};
  }

  public boolean isAllowedToPackage(ContainerElement element) {
    return getAllowedPackagingMethods(element).length > 0;
  }

  public Module getModule() {
    return myModule;
  }

  protected List<AddPackagingElementAction> getAddActions() {
    List<AddPackagingElementAction> actions = new ArrayList<AddPackagingElementAction>();
    actions.add(new AddPackagingElementAction(ProjectBundle.message("action.name.packaging.add.library"), Icons.LIBRARY_ICON) {
      public boolean isEnabled(@NotNull final PackagingEditor editor) {
        return !getLibrariesToAdd(editor).isEmpty();
      }

      public void perform(final PackagingEditor packagingEditor) {
        ChooseLibrariesDialog dialog = new ChooseLibrariesDialog(packagingEditor.getMainPanel(),
                                                                 ProjectBundle.message("dialog.title.packaging.choose.library"),
                                                                 getLibrariesToAdd(packagingEditor));
        dialog.show();
        if (dialog.isOK()) {
          packagingEditor.addLibraries(dialog.getChosenElements());
        }
      }
    });
    actions.add(new AddPackagingElementAction(ProjectBundle.message("action.name.packaging.add.module"), StdModuleTypes.JAVA.getNodeIcon(false)) {
      public boolean isEnabled(@NotNull final PackagingEditor editor) {
        return !getModulesToAdd(editor).isEmpty();
      }

      public void perform(final PackagingEditor packagingEditor) {
        ChooseModulesDialog dialog = new ChooseModulesDialog(packagingEditor.getMainPanel(), getModulesToAdd(packagingEditor),
                                                             ProjectBundle.message("dialog.title.packaging.choose.module"));
        dialog.show();
        List<Module> modules = new ArrayList<Module>(dialog.getChosenElements());
        if (dialog.isOK()) {
          packagingEditor.addModules(modules);
        }
      }
    });
    return actions;
  }

  private List<Library> getLibrariesToAdd(final PackagingEditor editor) {
    List<Library> libraries = getSuitableLibraries(editor);
    Set<Library> addedLibraries = new HashSet<Library>();
    for (ContainerElement element : getModifiedElements(editor)) {
      if (element instanceof LibraryLink) {
        addedLibraries.add(((LibraryLink)element).getLibrary());
      }
    }
    libraries.removeAll(addedLibraries);
    return libraries;
  }

  private List<Module> getModulesToAdd(final PackagingEditor editor) {
    List<Module> moduleList = getSuitableModules(editor);
    Set<Module> addedModules = new HashSet<Module>();
    for (ContainerElement element : getModifiedElements(editor)) {
      if (element instanceof ModuleLink) {
        addedModules.add(((ModuleLink)element).getModule());
      }
    }
    moduleList.removeAll(addedModules);
    return moduleList;
  }

  public boolean removeObsoleteElements(final PackagingEditor packagingEditor) {
    boolean modelChanged = false;
    Set<Library> libraries = new HashSet<Library>(getSuitableLibraries(packagingEditor));
    Set<Module> modules = new HashSet<Module>(getSuitableModules(packagingEditor));
    List<ContainerElement> elements = new ArrayList<ContainerElement>(Arrays.asList(getModifiedElements(packagingEditor)));
    PackagingConfiguration configuration = packagingEditor.getModifiedConfiguration();
    for (ContainerElement element : elements) {
      boolean remove = false;
      if (element instanceof LibraryLink) {
        Library library = ((LibraryLink)element).getLibrary();
        remove = library != null && !libraries.contains(library);
      }
      else if (element instanceof ModuleLink) {
        Module module = ((ModuleLink)element).getModule();
        remove = module != null && !modules.contains(module);
      }
      if (remove) {
        configuration.removeContainerElement(element);
        modelChanged = true;
      }
    }
    return modelChanged;
  }

  public String getElementText(final ContainerElement element) {
    if (element instanceof LibraryLink) {
      final LibraryLink libraryLink = (LibraryLink)element;
      Library library = libraryLink.getLibrary();
      if (library != null) {
        String name = library.getName();
        VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
        if (name != null) {
          return "'" + name + "' " + PackagingEditorUtil.getLibraryTableDisplayName(library);
        }
        else if (files.length > 0) {
          Module module = ((LibraryImpl)library).getModule();
          final String description;
          if (module == null) {
            description = "(" + PackagingEditorUtil.getLibraryTableDisplayName(library) + ")";
          }
          else {
            description = ProjectBundle.message("node.text.library.of.module", module.getName());
          }
          return files[0].getName() + " " + description;
        }
      }
    }
    if (element instanceof ModuleLink) {
      final ModuleLink moduleLink = (ModuleLink)element;
      return ProjectBundle.message("text.module.link.description.0.module", moduleLink.getName());
    }
    return element.getPresentableName();
  }

  protected static abstract class AdditionalPropertiesConfigurable {

    public abstract JComponent getLabelsComponent();
    public abstract JComponent getFieldsComponent();

    public abstract void resetFrom(ContainerElement element);
    public abstract void applyTo(ContainerElement element);
  }
}
