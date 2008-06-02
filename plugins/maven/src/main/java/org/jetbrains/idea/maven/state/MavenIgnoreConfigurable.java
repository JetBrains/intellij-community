package org.jetbrains.idea.maven.state;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.IdeaAPIHelper;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
import org.jetbrains.idea.maven.core.util.Strings;
import org.jetbrains.idea.maven.project.MavenProjectModel;

import javax.swing.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenIgnoreConfigurable implements Configurable {
  private JPanel panel;
  private ElementsChooser<VirtualFile> myFileChooser;
  private JTextArea myMaskEditor;

  private final MavenProjectsManager myManager;
  private final Collection<VirtualFile> myOriginalFiles;
  private String myOriginalMasks;

  private static final char SEPARATOR = ',';

  public MavenIgnoreConfigurable(MavenProjectsManager manager) {
    myManager = manager;

    myOriginalFiles = new HashSet<VirtualFile>();
    for (MavenProjectModel each : myManager.getProjects()) {
      if (myManager.getIgnoredFlag(each)) {
        myOriginalFiles.add(each.getFile());
      }
    }

    myOriginalMasks = Strings.detokenize(myManager.getIgnoredPathMasks(), SEPARATOR);
  }

  private void createUIComponents() {
    myFileChooser = new ElementsChooser<VirtualFile>(true) {
      protected String getItemText(final VirtualFile virtualFile) {
        return virtualFile != null ? virtualFile.getPath() : "";
      }
    };
  }

  @Nls
  public String getDisplayName() {
    return StateBundle.message("maven.tab.ignore");
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
    return !IdeaAPIHelper.equalAsSets(myOriginalFiles, myFileChooser.getMarkedElements()) ||
           !myOriginalMasks.equals(myMaskEditor.getText());
  }

  public void apply() throws ConfigurationException {
    final List<VirtualFile> marked = myFileChooser.getMarkedElements();
    for (MavenProjectModel each : myManager.getProjects()) {
      myManager.setIgnoredFlag(each, marked.contains(each.getFile()));
    }

    myManager.setIgnoredPathMasks(Strings.tokenize(myMaskEditor.getText(), Strings.WHITESPACE + SEPARATOR));
  }

  public void reset() {
    IdeaAPIHelper.addElements(myFileChooser, myManager.getFiles(), myOriginalFiles, ProjectUtil.ourProjectDirComparator);

    myMaskEditor.setText(myOriginalMasks);
  }

  public void disposeUIResources() {
  }
}
