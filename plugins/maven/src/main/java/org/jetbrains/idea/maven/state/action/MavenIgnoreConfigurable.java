package org.jetbrains.idea.maven.state.action;

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
import org.jetbrains.idea.maven.state.MavenProjectsState;
import org.jetbrains.idea.maven.state.StateBundle;

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

  private final MavenProjectsState myState;
  private final Collection<VirtualFile> myOriginalFiles;
  private String myOriginalMasks;

  private static final char SEPARATOR = ',';

  public MavenIgnoreConfigurable(MavenProjectsState state) {
    myState = state;

    myOriginalFiles = new HashSet<VirtualFile>();
    for (VirtualFile file : myState.getFiles()) {
      if (myState.getIgnoredFlag(file)) {
        myOriginalFiles.add(file);
      }
    }

    myOriginalMasks = Strings.detokenize(myState.getIgnoredPathMasks(), SEPARATOR);
  }

  private void createUIComponents() {
    myFileChooser = new ElementsChooser<VirtualFile>(true) {
      protected String getItemText(final VirtualFile virtualFile) {
        return virtualFile.getPath();
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
    for (VirtualFile file : myState.getFiles()) {
      myState.setIgnoredFlag(file, marked.contains(file));
    }

    myState.setIgnoredPathMasks(Strings.tokenize(myMaskEditor.getText(), " \t\n\r\f" + SEPARATOR));
  }

  public void reset() {
    IdeaAPIHelper.addElements( myFileChooser, myState.getFiles(), myOriginalFiles, ProjectUtil.ourProjectDirComparator);

    myMaskEditor.setText(myOriginalMasks);
  }

  public void disposeUIResources() {
  }
}
