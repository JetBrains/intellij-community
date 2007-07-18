package com.intellij.ide.util.importProject;

import com.intellij.ide.util.newProjectWizard.ProjectFromSourcesBuilder;
import com.intellij.ide.util.projectWizard.AbstractStepWithProgress;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 6, 2004
 */
public class ProjectLayoutDetectionStep extends AbstractStepWithProgress<ProjectLayout> {
  private final ProjectFromSourcesBuilder myBuilder;
  private final Icon myIcon;
  private final String myHelpId;
  private LibrariesLayoutPanel myLibrariesPanel;

  public ProjectLayoutDetectionStep(ProjectFromSourcesBuilder builder, Icon icon, @NonNls String helpId) {
    super("Stop project layout analysis?");
    myBuilder = builder;
    myIcon = icon;
    myHelpId = helpId;
  }

  public void updateDataModel() {
  }

  protected JComponent createResultsPanel() {
    myLibrariesPanel = new LibrariesLayoutPanel(myBuilder);
    return myLibrariesPanel;
  }

  protected String getProgressText() {
    return "Analyzing module structure. Please wait.";
  }

  protected boolean shouldRunProgress() {
    // todo!!
    return true;
  }

  protected ProjectLayout calculate() {
    // build sources array
    final List<Pair<String,String>> sourcePaths = myBuilder.getSourcePaths();
    final List<Pair<File,String>> _sourcePaths = new ArrayList<Pair<File, String>>();
    for (Pair<String, String> path : sourcePaths) {
      _sourcePaths.add(new Pair<File, String>(new File(path.first), path.second != null? path.second : ""));
    }
    // build ignored names set
    final HashSet<String> ignored = new HashSet<String>();
    final StringTokenizer tokenizer = new StringTokenizer(FileTypeManager.getInstance().getIgnoredFilesList(), ";", false);
    while (tokenizer.hasMoreTokens()) {
      ignored.add(tokenizer.nextToken());
    }
    // start scan
    final ModuleInsight insight = new ModuleInsight(new DelegatingProgressIndicator(), Arrays.asList(new File(myBuilder.getContentRootPath())), _sourcePaths, ignored);
    insight.scan();
    
    return insight.getSuggestedLayout();
  }

  protected void onFinished(final ProjectLayout layout, final boolean canceled) {
    myBuilder.setProjectLayout(layout);
    myLibrariesPanel.rebuild();
  }
  
  public Icon getIcon() {
    return myIcon;
  }

  public String getHelpId() {
    return myHelpId;
  }
  
  /*
  private static final class MyTreeCellRenderer extends DefaultTreeCellRenderer {
    private static final Icon ICON_MODULE = IconLoader.getIcon("/nodes/ModuleClosed.png");
    private static final Icon ICON_LIBRARY = IconLoader.getIcon("/modules/library.png");
    private static final Icon ICON_JAR = IconLoader.getIcon("/fileTypes/archive.png");
    public Component getTreeCellRendererComponent(final JTree tree, final Object value, final boolean sel, final boolean expanded,
                                                  final boolean leaf, final int row, final boolean hasFocus) {
      final Component comp = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      final Object userObject = value instanceof DefaultMutableTreeNode? ((DefaultMutableTreeNode)value).getUserObject() : null;
      if (userObject instanceof ModuleDescriptor) {
        setIcon(ICON_MODULE);
      }
      else if (userObject instanceof LibraryDescriptor) {
        final int jarsCount = ((LibraryDescriptor)userObject).getJars().size();
        if (jarsCount == 1) {
          setIcon(ICON_JAR);
        }
        else {
          setIcon(ICON_LIBRARY);
        }
      }
      return comp;
    }
  }
  */
}