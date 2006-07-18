package com.intellij.ide.structureView.impl.jsp;

import com.intellij.ide.structureView.StructureView;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.TabbedPaneWrapper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * @author cdr
 */
public class StructureViewComposite implements StructureView {
  @NotNull private final StructureViewDescriptor[] myStructureViews;
  @NotNull private StructureViewDescriptor mySelectedViewDescriptor;
  @NotNull private final TabbedPaneWrapper myTabbedPaneWrapper;

  public static class StructureViewDescriptor {
    public String title;
    public StructureView structureView;
    public Icon icon;

    public StructureViewDescriptor(final String title, final StructureView structureView, Icon icon) {
      this.title = title;
      this.structureView = structureView;
      this.icon = icon;
    }
  }

  public StructureViewComposite(@NotNull StructureViewDescriptor... views) {
    myStructureViews = views;
    for (StructureViewDescriptor descriptor : views) {
      Disposer.register(this, descriptor.structureView);
    }
    mySelectedViewDescriptor = views[0];
    myTabbedPaneWrapper = new TabbedPaneWrapper();
    for (StructureViewDescriptor descriptor : views) {
      myTabbedPaneWrapper.addTab(descriptor.title, descriptor.icon, descriptor.structureView.getComponent(), null);
    }
    myTabbedPaneWrapper.setSelectedIndex(0);
    myTabbedPaneWrapper.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        int index = myTabbedPaneWrapper.getSelectedIndex();
        mySelectedViewDescriptor = myStructureViews[index];
      }
    });
  }

  public StructureView getSelectedStructureView() {
    return mySelectedViewDescriptor.structureView;
  }

  public FileEditor getFileEditor() {
    return getSelectedStructureView().getFileEditor();
  }

  public boolean navigateToSelectedElement(final boolean requestFocus) {
    return getSelectedStructureView().navigateToSelectedElement(requestFocus);
  }

  public JComponent getComponent() {
    return myTabbedPaneWrapper.getComponent();
  }

  public void dispose() {
  }

  public void centerSelectedRow() {
    getSelectedStructureView().centerSelectedRow();
  }

  public void restoreState() {
    for (StructureViewDescriptor descriptor : myStructureViews) {
      descriptor.structureView.restoreState();
    }
  }

  public void storeState() {
    for (StructureViewDescriptor descriptor : myStructureViews) {
      descriptor.structureView.storeState();
    }
  }
}
