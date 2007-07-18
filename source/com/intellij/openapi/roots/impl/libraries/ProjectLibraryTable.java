package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 *  @author dsl
 */
@State(
  name = "libraryTable",
  storages = {
    @Storage(id = "default", file = "$PROJECT_FILE$")
   ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/libraries/", scheme = StorageScheme.DIRECTORY_BASED, stateSplitter = ProjectLibraryTable.LibraryStateSplitter.class)
    }
)
public class ProjectLibraryTable extends LibraryTableBase implements ProjectComponent {
  private static final LibraryTablePresentation PROJECT_LIBRARY_TABLE_PRESENTATION = new LibraryTablePresentation() {
    public String getDisplayName(boolean plural) {
      return ProjectBundle.message("project.library.display.name", plural ? 2 : 1);
    }

    public String getDescription() {
      return ProjectBundle.message("libraries.node.text.project");
    }

    public String getLibraryTableEditorTitle() {
      return ProjectBundle.message("library.configure.project.title");
    }
  };

  ProjectLibraryTable() {

  }
  public static LibraryTable getInstance(Project project) {
    return project.getComponent(LibraryTable.class);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public String getTableLevel() {
    return LibraryTablesRegistrar.PROJECT_LEVEL;
  }

  public LibraryTablePresentation getPresentation() {
    return PROJECT_LIBRARY_TABLE_PRESENTATION;
  }

  public boolean isEditable() {
    return true;
  }


  public static class LibraryStateSplitter implements StateSplitter {

    public List<Pair<Element, String>> splitState(Element e) {
      final UniqueNameGenerator generator = new UniqueNameGenerator();

      List<Pair<Element, String>> result = new ArrayList<Pair<Element, String>>();

      final List list = e.getChildren();
      for (final Object o : list) {
        Element library = (Element)o;
        @NonNls final String name = generator.generateUniqueName(FileUtil.sanitizeFileName(library.getAttributeValue(LibraryImpl.LIBRARY_NAME_ATTR))) + ".xml";
        result.add(new Pair<Element, String>(library, name));
      }

      return result;
    }

    public void mergeStatesInto(Element target, Element[] elements) {
      for (Element e : elements) {
        target.addContent(e);
      }
    }
  }
}
