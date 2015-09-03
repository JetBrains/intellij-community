package org.jetbrains.idea.maven.utils.library;

import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryPropertiesEditorBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.library.propertiesEditor.RepositoryLibraryPropertiesDialog;
import org.jetbrains.idea.maven.utils.library.propertiesEditor.RepositoryLibraryPropertiesModel;

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
    //String oldVersion = properties.getVersion();
    boolean wasGeneratedName =
      RepositoryLibraryType.getInstance().getDescription(properties).equals(myEditorComponent.getLibraryEditor().getName());
    RepositoryLibraryPropertiesModel model = new RepositoryLibraryPropertiesModel(
      properties.getVersion(),
      RepositoryUtils.libraryHasSources(myEditorComponent.getLibraryEditor()),
      RepositoryUtils.libraryHasJavaDocs(myEditorComponent.getLibraryEditor()));
    RepositoryLibraryPropertiesDialog dialog = new RepositoryLibraryPropertiesDialog(
      myEditorComponent.getProject(),
      model,
      RepositoryLibraryDescription.findDescription(properties),
      true);
    if (!dialog.showAndGet()) {
      return;
    }
    myEditorComponent.getProperties().changeVersion(model.getVersion());
    if (wasGeneratedName) {
      myEditorComponent.renameLibrary(RepositoryLibraryType.getInstance().getDescription(properties));
    }
    myEditorComponent.getLibraryEditor().removeAllRoots();
    myEditorComponent.getLibraryEditor().addRoots(RepositoryUtils.download(
      myEditorComponent.getProject(),
      model.isDownloadSources(),
      model.isDownloadJavaDocs(),
      properties));
  }
}
