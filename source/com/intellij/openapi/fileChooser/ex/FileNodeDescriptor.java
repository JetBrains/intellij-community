package com.intellij.openapi.fileChooser.ex;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

public class FileNodeDescriptor extends NodeDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.chooser.FileNodeDescriptor");

  private FileElement myDescriptor;
  private Icon myOriginalOpenIcon;
  private Icon myOriginalClosedIcon;

  public FileNodeDescriptor(Project project, FileElement element, NodeDescriptor parentDescriptor, Icon openIcon, Icon closedIcon) {
    super(project, parentDescriptor);
    myOriginalOpenIcon = openIcon;
    myOriginalClosedIcon = closedIcon;
    LOG.assertTrue(element != null);
    myDescriptor = element;
  }

  public boolean update() {
    boolean changed = false;

    if (!myDescriptor.toString().equals(myName)) changed = true;

    myName = myDescriptor.toString();

    VirtualFile file = myDescriptor.getFile();

    if (file == null) return true;

    myOpenIcon = myOriginalOpenIcon;
    myClosedIcon = myOriginalClosedIcon;
    if (myDescriptor.isHidden()) {
      myOpenIcon = IconLoader.getTransparentIcon(myOpenIcon);
      myClosedIcon = IconLoader.getTransparentIcon(myClosedIcon);
    }
    myColor = myDescriptor.isHidden() ? SimpleTextAttributes.DARK_TEXT.getFgColor() : null;
    return changed;
  }

  public final Object getElement() {
    return myDescriptor;
  }

  protected final void setElement(FileElement descriptor) {
    myDescriptor = descriptor;
  }
}
