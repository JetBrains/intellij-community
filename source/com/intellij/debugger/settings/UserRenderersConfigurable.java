/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.settings;

import com.intellij.debugger.ui.tree.render.CompoundNodeRenderer;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.containers.InternalIterator;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 19, 2005
 */
public class UserRenderersConfigurable implements Configurable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.settings.UserRenderersConfigurable");
  private static final Icon ADD_ICON = IconLoader.getIcon("/general/add.png");
  private static final Icon REMOVE_ICON = IconLoader.getIcon("/general/remove.png");
  private static final Icon COPY_ICON = IconLoader.getIcon("/general/copy.png");
  private static final Icon UP_ICON = IconLoader.getIcon("/actions/previousOccurence.png");
  private static final Icon DOWN_ICON = IconLoader.getIcon("/actions/nextOccurence.png");

  private JTextField myNameField;
  private ElementsChooser<NodeRenderer> myRendererChooser;
  private NodeRenderer myCurrentRenderer = null;
  private final CompoundRendererConfigurable myRendererDataConfigurable;

  public UserRenderersConfigurable(Project project) {
    myRendererDataConfigurable = new CompoundRendererConfigurable(project);
  }

  public String getDisplayName() {
    return "Type Renderers";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null; // todo
  }

  public JComponent createComponent() {
    final JPanel panel = new JPanel(new GridBagLayout());

    final JComponent renderersList = createRenderersList();
    final JComponent toolbar = createToolbar();
    final JComponent rendererDataPanel = myRendererDataConfigurable.createComponent();

    panel.add(toolbar, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(6, 0, 0, 0), 0, 0));
    panel.add(renderersList, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(2, 0, 0, 0), 0, 0));

    myNameField = new JTextField();
    final JPanel nameFieldPanel = new JPanel(new BorderLayout());
    nameFieldPanel.add(new JLabel("Renderer name:"), BorderLayout.WEST);
    nameFieldPanel.add(myNameField, BorderLayout.CENTER);
    panel.add(nameFieldPanel, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(6, 6, 0, 6), 0, 0));

    panel.add(rendererDataPanel, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(10, 6, 10, 6), 0, 0));

    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        if (myCurrentRenderer != null) {
          myCurrentRenderer.setName(myNameField.getText());
          myRendererChooser.refresh(myCurrentRenderer);
        }
      }
    });

    return panel;
  }

  private JComponent createRenderersList() {
    myRendererChooser = new ElementsChooser<NodeRenderer>();
    myRendererChooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<NodeRenderer>() {
      public void elementMarkChanged(final NodeRenderer element, final boolean isMarked) {
        element.setEnabled(isMarked);
      }
    });
    myRendererChooser.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        final java.util.List<NodeRenderer> selectedElements = myRendererChooser.getSelectedElements();
        if (selectedElements.size() != 1) {
          // multiselection
          setCurrentRenderer(null);
        }
        else {
          setCurrentRenderer(selectedElements.get(0));
        }
      }
    });
    return myRendererChooser;
  }

  private void setCurrentRenderer(NodeRenderer renderer) {
    if (myCurrentRenderer == renderer) {
      return;
    }
    try {
      if (myRendererDataConfigurable.isModified()) {
        myRendererDataConfigurable.apply();
      }
    }
    catch (ConfigurationException e) {
      LOG.error(e);
    }
    myCurrentRenderer = renderer;
    if (renderer != null) {
      myNameField.setEnabled(true);
      myNameField.setText(renderer.getName());
    }
    else {
      myNameField.setEnabled(false);
      myNameField.setText("");
    }
    myRendererDataConfigurable.setRenderer(renderer);
  }

  private JComponent createToolbar() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AddAction());
    group.add(new RemoveAction());
    group.add(new CopyAction());
    group.add(new MoveAction(true));
    group.add(new MoveAction(false));
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    return toolbar.getComponent();
  }

  public void apply() throws ConfigurationException {
    myRendererDataConfigurable.apply();
    flushTo(NodeRendererSettings.getInstance().getCustomRenderers());
  }

  private void flushTo(final RendererConfiguration rendererConfiguration) {
    rendererConfiguration.removeAllRenderers();
    final int count = myRendererChooser.getElementCount();
    for (int idx = 0; idx < count; idx++) {
      rendererConfiguration.addRenderer(myRendererChooser.getElementAt(idx));
    }
  }

  public boolean isModified() {
    if (myRendererDataConfigurable.isModified()) {
      return true;
    }
    final NodeRendererSettings settings = NodeRendererSettings.getInstance();
    final RendererConfiguration rendererConfiguration = settings.getCustomRenderers();
    if (myRendererChooser.getElementCount() != rendererConfiguration.getRendererCount()) {
      return true;
    }
    final RendererConfiguration uiConfiguration = new RendererConfiguration(settings);
    flushTo(uiConfiguration);
    return !uiConfiguration.equals(rendererConfiguration);
  }

  public void reset() {
    myRendererChooser.removeAllElements();
    final RendererConfiguration rendererConfiguration = NodeRendererSettings.getInstance().getCustomRenderers();
    final ArrayList<NodeRenderer> elementsToSelect = new ArrayList<NodeRenderer>(1);
    rendererConfiguration.iterateRenderers(new InternalIterator<NodeRenderer>() {
      public boolean visit(final NodeRenderer renderer) {
        final NodeRenderer clonedRenderer = (NodeRenderer)renderer.clone();
        myRendererChooser.addElement(clonedRenderer, clonedRenderer.isEnabled());
        if (elementsToSelect.size() == 0) {
          elementsToSelect.add(clonedRenderer);
        }
        return true;
      }
    });
    if (elementsToSelect.size() > 0) {
      myRendererChooser.selectElements(elementsToSelect);
    }
    myRendererDataConfigurable.reset();
  }

  public void disposeUIResources() {
    myRendererDataConfigurable.disposeUIResources();
  }

  private class AddAction extends AnAction {
    public AddAction() {
      super("Add", "Add new renderer", ADD_ICON);
    }

    public void actionPerformed(AnActionEvent e) {
      final NodeRenderer renderer = (NodeRenderer)NodeRendererSettings.getInstance().createRenderer(CompoundNodeRenderer.UNIQUE_ID);
      renderer.setEnabled(true);
      myRendererChooser.addElement(renderer, renderer.isEnabled());
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myNameField.requestFocus();
        }
      });
    }
  }

  private class RemoveAction extends AnAction {
    public RemoveAction() {
      super("Remove", "Remove selected renderer", REMOVE_ICON);
    }

    public void actionPerformed(AnActionEvent e) {
      final NodeRenderer selectedElement = myRendererChooser.getSelectedElement();
      if (selectedElement != null) {
        myRendererChooser.removeElement(selectedElement);
      }
    }

    public void update(AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(myRendererChooser.getSelectedElement() != null);
    }
  }

  private class CopyAction extends AnAction {
    public CopyAction() {
      super("Copy", "Copy selected renderer", COPY_ICON);
    }

    public void actionPerformed(AnActionEvent e) {
      final NodeRenderer selectedElement = myRendererChooser.getSelectedElement();
      if (selectedElement != null) {
        final NodeRenderer cloned = (NodeRenderer)selectedElement.clone();
        myRendererChooser.addElement(cloned, true);
      }
    }

    public void update(AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(myRendererChooser.getSelectedElement() != null);
    }
  }

  private class MoveAction extends AnAction {
    private final boolean myMoveUp;

    public MoveAction(boolean up) {
      super("Move " + (up? "Up" : "Down"), "Move renderer " + (up? "Up" : "Down"), (up? UP_ICON : DOWN_ICON) );
      myMoveUp = up;
    }

    public void actionPerformed(AnActionEvent e) {
      final int selectedRow = myRendererChooser.getSelectedElementRow();
      if (selectedRow < 0) {
        return;
      }
      int newRow = selectedRow + (myMoveUp? -1 : 1);
      if (newRow < 0) {
        newRow = myRendererChooser.getElementCount() - 1;
      }
      else if (newRow >= myRendererChooser.getElementCount()) {
        newRow = 0;
      }
      myRendererChooser.moveElement(myRendererChooser.getElementAt(selectedRow), newRow);
    }

    public void update(AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(myRendererChooser.getSelectedElement() != null);
    }
  }
}
