package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.deployment.ContainerElement;
import com.intellij.openapi.deployment.LibraryLink;
import com.intellij.openapi.deployment.ModuleLink;
import com.intellij.openapi.deployment.PackagingMethod;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ChooseModulesDialog;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

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

  public boolean isRelativePathCellEditable(final ContainerElement element) {
    return element instanceof LibraryLink
           || element instanceof ModuleLink && ((ModuleLink)element).getModule() != null;
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
                                                                 ProjectBundle.message("dialog.title.packaging.choose.library"), getLibrariesToAdd(packagingEditor));
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

  protected abstract List<Module> getModulesToAdd(final PackagingEditor packagingEditor);

  protected abstract List<Library> getLibrariesToAdd(final PackagingEditor packagingEditor);
}
