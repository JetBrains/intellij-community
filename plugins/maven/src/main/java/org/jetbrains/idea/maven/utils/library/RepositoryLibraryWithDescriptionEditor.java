package org.jetbrains.idea.maven.utils.library;

import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryPropertiesEditorBase;
import org.jetbrains.annotations.NotNull;

public class RepositoryLibraryWithDescriptionEditor
  extends LibraryPropertiesEditorBase<RepositoryLibraryProperties, RepositoryLibraryType> {

  public RepositoryLibraryWithDescriptionEditor(LibraryEditorComponent<RepositoryLibraryProperties> editorComponent) {
    super(editorComponent, RepositoryLibraryType.getInstance(), null);
  }

  @Override
  public void apply() {
  }

  @Override
  protected void edit() {
    @NotNull RepositoryLibraryProperties properties = myEditorComponent.getProperties();
    String oldVersion = properties.getVersion();
    boolean wasGeneratedName =
      RepositoryLibraryType.getInstance().getDescription(properties).equals(myEditorComponent.getLibraryEditor().getName());
    RepositoryLibraryPropertiesEditor editor = new RepositoryLibraryPropertiesEditor(
      myEditorComponent.getProject(),
      RepositoryUtils.libraryHasSources(myEditorComponent.getLibraryEditor()),
      RepositoryUtils.libraryHasJavaDocs(myEditorComponent.getLibraryEditor()),
      properties);
    editor.init();
    editor.setTitle(RepositoryLibraryDescription.findDescription(properties).getDisplayName());
    if (!editor.showAndGet()) {
      return;
    }
    myEditorComponent.getProperties().loadState(editor.getProperties());
    if (wasGeneratedName) {
      myEditorComponent.renameLibrary(RepositoryLibraryType.getInstance().getDescription(properties));
    }
    myEditorComponent.getLibraryEditor().removeAllRoots();
    myEditorComponent.getLibraryEditor().addRoots(RepositoryUtils.download(
      myEditorComponent.getProject(),
      editor.downloadSources(),
      editor.downloadJavaDocs(),
      properties));
  }
}
