package org.jetbrains.idea.maven.project;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
public class ImporterPreferencesConfigurable implements Configurable {
  private final MavenImporterPreferencesComponent myPreferencesComponent;
  private final MavenImporter myImporter;

  private final ElementsChooser<String> myChooser = new ElementsChooser<String>(true);

  private JPanel panel;
  private JPanel wrapperPanel;
  private ImporterPreferencesForm preferencesForm;

  public ImporterPreferencesConfigurable(final MavenImporterPreferencesComponent preferencesComponent, final MavenImporter importer) {
    myPreferencesComponent = preferencesComponent;
    myImporter = importer;
    wrapperPanel.setLayout(new BorderLayout());
    wrapperPanel.add(myChooser);
  }

  private List<String> getOriginal() {
    return myImporter.getState().getRememberedFiles();
  }

  private List<String> getUnmarked() {
    List<String> unmarked = getOriginal();
    unmarked.removeAll(myChooser.getMarkedElements());
    return unmarked;
  }

  @Nls
  public String getDisplayName() {
    return ProjectBundle.message("maven.tab.import");
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return panel;
  }

  public boolean isModified() {
    return preferencesForm.isModified(myPreferencesComponent.getState()) || getUnmarked().size() != 0;
  }

  public void apply() throws ConfigurationException {
    preferencesForm.getData(myPreferencesComponent.getState());
    for (String path : getUnmarked()) {
      myImporter.getState().forget(path);
    }
  }

  public void reset() {
    preferencesForm.setData(myPreferencesComponent.getState());
    myChooser.setElements(getOriginal(), true);
  }

  public void disposeUIResources() {
  }
}