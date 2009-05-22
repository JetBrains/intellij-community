package org.jetbrains.idea.maven.project;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenUIUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.Strings;

import javax.swing.*;
import java.util.Collection;
import java.util.Comparator;

public class MavenIgnoredFilesConfigurable implements Configurable {
  private static final char SEPARATOR = ',';

  private final MavenProjectsManager myManager;

  private Collection<String> myOriginallyIgnoredFilesPaths;
  private String myOriginallyIgnoredFilesPatterns;

  private JPanel myMainPanel;
  private ElementsChooser<String> myIgnoredFilesPathsChooser;
  private JTextArea myIgnoredFilesPattersEditor;

  public MavenIgnoredFilesConfigurable(MavenProjectsManager manager) {
    myManager = manager;
  }

  private void createUIComponents() {
    myIgnoredFilesPathsChooser = new ElementsChooser<String>(true);
  }

  public JComponent createComponent() {
    return myMainPanel;
  }

  public void disposeUIResources() {
  }

  public boolean isModified() {
    return !MavenUtil.equalAsSets(myOriginallyIgnoredFilesPaths, myIgnoredFilesPathsChooser.getMarkedElements()) ||
           !myOriginallyIgnoredFilesPatterns.equals(myIgnoredFilesPattersEditor.getText());
  }

  public void apply() throws ConfigurationException {
    myManager.setIgnoredFilesPaths(myIgnoredFilesPathsChooser.getMarkedElements());
    myManager.setIgnoredFilesPatterns(Strings.tokenize(myIgnoredFilesPattersEditor.getText(), Strings.WHITESPACE + SEPARATOR));
  }

  public void reset() {
    myOriginallyIgnoredFilesPaths = myManager.getIgnoredFilesPaths();
    myOriginallyIgnoredFilesPatterns = Strings.detokenize(myManager.getIgnoredFilesPatterns(), SEPARATOR);

    MavenUIUtil.setElements(myIgnoredFilesPathsChooser,
                            MavenUtil.collectPaths(myManager.getProjectsFiles()),
                            myOriginallyIgnoredFilesPaths,
                            new Comparator<String>() {
                              public int compare(String o1, String o2) {
                                return FileUtil.comparePaths(o1, o2);
                              }
                            });
    myIgnoredFilesPattersEditor.setText(myOriginallyIgnoredFilesPatterns);
  }

  @Nls
  public String getDisplayName() {
    return ProjectBundle.message("maven.tab.ignored.files");
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settings.project.maven.ignored.files";
  }
}
