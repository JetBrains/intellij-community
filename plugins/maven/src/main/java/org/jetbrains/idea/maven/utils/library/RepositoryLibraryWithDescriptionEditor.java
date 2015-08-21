package org.jetbrains.idea.maven.utils.library;

import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryPropertiesEditorBase;
import org.jetbrains.annotations.NotNull;

public class RepositoryLibraryWithDescriptionEditor
  extends LibraryPropertiesEditorBase<RepositoryLibraryProperties, RepositoryLibraryType> {
  private final RepositoryLibraryType libraryType;

  public RepositoryLibraryWithDescriptionEditor(LibraryEditorComponent<RepositoryLibraryProperties> editorComponent,
                                                RepositoryLibraryType libraryType) {
    super(editorComponent, libraryType, null);
    this.libraryType = libraryType;
  }

  @Override
  public void apply() {

  }

  @Override
  protected void edit() {
    @NotNull RepositoryLibraryProperties properties = myEditorComponent.getProperties();
    String oldVersion = properties.getVersion();
    boolean wasGeneratedName = libraryType.getDescription(properties).equals(myEditorComponent.getLibraryEditor().getName());
    RepositoryLibraryPropertiesEditor editor = new RepositoryLibraryPropertiesEditor(
      myEditorComponent.getProject(),
      properties);
    editor.init();
    editor.setTitle(libraryType.getDescription(properties));
    if (!editor.showAndGet() || oldVersion.equals(editor.getSelectedVersion())) {
      return;
    }
    myEditorComponent.getProperties().loadState(editor.getProperties());
    if (wasGeneratedName) {
      myEditorComponent.renameLibrary(libraryType.getDescription(properties));
    }
    myEditorComponent.getLibraryEditor().removeAllRoots();
    myEditorComponent.getLibraryEditor().addRoots(RepositoryUtils.download(myEditorComponent.getProject(), properties));
  }
}
