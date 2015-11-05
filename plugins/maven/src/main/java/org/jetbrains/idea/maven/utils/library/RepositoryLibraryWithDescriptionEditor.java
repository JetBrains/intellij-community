package org.jetbrains.idea.maven.utils.library;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryPropertiesEditorBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.library.propertiesEditor.RepositoryLibraryPropertiesDialog;
import org.jetbrains.idea.maven.utils.library.propertiesEditor.RepositoryLibraryPropertiesModel;
import org.jetbrains.idea.maven.utils.library.remote.MavenDependenciesRemoteManager;
import org.jetbrains.idea.maven.utils.library.remote.MavenRemoteTask;

import java.util.List;

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

    final LibraryEditor libraryEditor = myEditorComponent.getLibraryEditor();
    MavenDependenciesRemoteManager.getInstance(myEditorComponent.getProject())
      .downloadDependenciesAsync(
        properties,
        model.isDownloadSources(),
        model.isDownloadJavaDocs(),
        RepositoryUtils.getStorageRoot(myEditorComponent.getLibraryEditor().getUrls(OrderRootType.CLASSES), myEditorComponent.getProject()),
        new MavenRemoteTask.ResultProcessor<List<OrderRoot>>() {
          @Override
          public void process(@Nullable List<OrderRoot> roots) {
            libraryEditor.removeAllRoots();
            if (roots != null) {
              libraryEditor.addRoots(roots);
            }
          }
        }
      );
  }
}
