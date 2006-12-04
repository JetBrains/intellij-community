package com.intellij.openapi.roots.impl.libraries;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ExpandMacroToPathMap;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.util.*;
import com.intellij.openapi.project.ProjectBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 *  @author dsl
 */
public class ApplicationLibraryTable extends LibraryTableBase implements NamedJDOMExternalizable, ExportableApplicationComponent {
  private PathMacrosImpl myPathMacros;
  public static final LibraryTablePresentation GLOBAL_LIBRARY_TABLE_PRESENTATION = new LibraryTablePresentation() {
    public String getDisplayName(boolean plural) {
      return ProjectBundle.message("global.library.display.name", plural ? 2 : 1);
    }

    public String getDescription() {
      return ProjectBundle.message("libraries.node.text.ide");
    }

    public String getLibraryTableEditorTitle() {
      return ProjectBundle.message("library.configure.global.title");
    }
  };

  public ApplicationLibraryTable(PathMacrosImpl pathMacros) {
    myPathMacros = pathMacros;
  }

  public String getTableLevel() {
    return LibraryTablesRegistrar.APPLICATION_LEVEL;
  }

  public LibraryTablePresentation getPresentation() {
    return GLOBAL_LIBRARY_TABLE_PRESENTATION;
  }

  public boolean isEditable() {
    return true;
  }

  public String getExternalFileName() {
    return "applicationLibraries";
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this)};
  }

  @NotNull
  public String getPresentableName() {
    return ProjectBundle.message("library.global.settings");
  }

  public static LibraryTable getInstance() {
    return ApplicationManager.getApplication().getComponent(LibraryTable.class);
  }

  public void readExternal(Element element) throws InvalidDataException {
    final ExpandMacroToPathMap macroExpands = new ExpandMacroToPathMap();
    myPathMacros.addMacroExpands(macroExpands);
    macroExpands.substitute(element, SystemInfo.isFileSystemCaseSensitive);
    super.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    final ReplacePathToMacroMap macroReplacements = new ReplacePathToMacroMap();
    PathMacrosImpl.getInstanceEx().addMacroReplacements(macroReplacements);
    macroReplacements.substitute(element, SystemInfo.isFileSystemCaseSensitive);
  }
}
