/*
 * @author max
 */
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TitlePanel;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class DetectedSourceRootsDialog extends DialogWrapper {
  private final ElementsChooser<VirtualFile> myChooser;
  private final VirtualFile myBaseRoot;

  public DetectedSourceRootsDialog(Project project, List<VirtualFile> detectedRoots, VirtualFile baseRoot) {
    super(project, true);
    myBaseRoot = baseRoot;
    myChooser = new ElementsChooser<VirtualFile>(detectedRoots, true) {
      protected String getItemText(final VirtualFile value) {
        return VfsUtil.getRelativePath(value, myBaseRoot, File.separatorChar);
      }

      protected Icon getItemIcon(final VirtualFile value) {
        return Icons.DIRECTORY_CLOSED_ICON;
      }
    };

    setTitle("Detected Source Roots");
    init();
  }

  protected JComponent createTitlePane() {
    return new TitlePanel("Choose Source Roots", "<html><body>IntelliJ IDEA just scanned '" + myBaseRoot.getPresentableUrl() +
                                                 "' and detected following source root(s).<br>" +
                                                 "Mark items in the list below or press Cancel to attach original root.</body></html>");
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myChooser;
  }

  public List<VirtualFile> getChosenRoots() {
    return myChooser.getMarkedElements();
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "DetectedSourceRootsDialog";
  }
}