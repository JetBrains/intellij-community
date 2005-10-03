package com.intellij.openapi.roots.impl.libraries;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ExpandMacroToPathMap;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.*;
import com.intellij.openapi.project.ProjectBundle;
import org.jdom.Element;

import java.io.File;

/**
 *  @author dsl
 */
public class ApplicationLibraryTable extends LibraryTableBase implements NamedJDOMExternalizable, ExportableApplicationComponent {
  private PathMacrosImpl myPathMacros;

  public ApplicationLibraryTable(PathMacrosImpl pathMacros) {
    myPathMacros = pathMacros;
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public String getTableLevel() {
    return LibraryTablesRegistrar.APPLICATION_LEVEL;
  }

  public String getExternalFileName() {
    return "applicationLibraries";
  }

  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this)};
  }

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
