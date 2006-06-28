package com.intellij.openapi.options.ex;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.util.Alarm;
import com.intellij.CommonBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class SingleConfigurableEditor extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.options.ex.SingleConfigurableEditor");
  private Project myProject;
  private Component myParentComponent;
  private Configurable myConfigurable;
  private JComponent myCenterPanel;
  private String myDimensionKey;

  public SingleConfigurableEditor(Project project, Configurable configurable, String dimensionKey) {
    super(project, true);
    myDimensionKey = dimensionKey;
    setTitle(createTitleString(configurable));

    myProject = project;
    myConfigurable = configurable;
    init();
    myConfigurable.reset();
  }

  public Configurable getConfigurable() {
    return myConfigurable;
  }

  public Project getProject() {
    return myProject;
  }

  public SingleConfigurableEditor(Project project, Configurable configurable) {
    this(project, configurable, null);
  }

  public SingleConfigurableEditor(Component parent, Configurable configurable) {
    this(parent, configurable, null);
  }

  private static String createTitleString(Configurable configurable) {
    String displayName = configurable.getDisplayName();
    LOG.assertTrue(displayName != null, configurable.getClass().getName());
    return displayName.replaceAll("\n", " ");
  }

  public SingleConfigurableEditor(Component parent, Configurable configurable, String dimensionServiceKey) {
    super(parent, true);
    myDimensionKey = dimensionServiceKey;
    setTitle(createTitleString(configurable));

    myParentComponent = parent;
    myConfigurable = configurable;
    init();
    myConfigurable.reset();
  }

  protected String getDimensionServiceKey() {
    if (myDimensionKey == null) {
      return super.getDimensionServiceKey();
    }
    else {
      return myDimensionKey;
    }
  }

  protected Action[] createActions() {
    if (myConfigurable.getHelpTopic() != null) {
      return new Action[]{getOKAction(), getCancelAction(), new ApplyAction(), getHelpAction()};
    }
    else {
      return new Action[]{getOKAction(), getCancelAction(), new ApplyAction()};
    }
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myConfigurable.getHelpTopic());
  }

  protected void doOKAction() {
    try {
      if (myConfigurable.isModified()) myConfigurable.apply();
    }
    catch (ConfigurationException e) {
      if (e.getMessage() != null) {
        if (myProject != null) {
          Messages.showMessageDialog(myProject, e.getMessage(), e.getTitle(), Messages.getErrorIcon());
        }
        else {
          Messages.showMessageDialog(myParentComponent, e.getMessage(), e.getTitle(), Messages.getErrorIcon());
        }
      }
      return;
    }
    super.doOKAction();
  }

  protected class ApplyAction extends AbstractAction {
    private Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

    public ApplyAction() {
      super(CommonBundle.getApplyButtonText());
      final Runnable updateRequest = new Runnable() {
        public void run() {
          if (!SingleConfigurableEditor.this.isShowing()) return;
          ApplyAction.this.setEnabled(myConfigurable != null && myConfigurable.isModified());
          addUpdateRequest(this);
        }
      };

      // invokeLater necessary to make sure dialog is already shown so we calculate modality state correctly.
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          addUpdateRequest(updateRequest);
        }
      });
    }

    private void addUpdateRequest(final Runnable updateRequest) {
      myUpdateAlarm.addRequest(updateRequest, 500, ModalityState.stateForComponent(getWindow()));
    }

    public void actionPerformed(ActionEvent event) {
      try {
        if (myConfigurable.isModified()) {
          myConfigurable.apply();
          setCancelButtonText(CommonBundle.getCloseButtonText());
        }
      }
      catch (ConfigurationException e) {
        if (myProject != null) {
          Messages.showMessageDialog(myProject, e.getMessage(), e.getTitle(), Messages.getErrorIcon());
        }
        else {
          Messages.showMessageDialog(myParentComponent, e.getMessage(), e.getTitle(),
                                     Messages.getErrorIcon());
        }
      }
    }
  }

  protected JComponent createCenterPanel() {
    myCenterPanel = myConfigurable.createComponent();
    return myCenterPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    if (myConfigurable instanceof BaseConfigurable) {
      JComponent preferred = ((BaseConfigurable)myConfigurable).getPreferredFocusedComponent();
      if (preferred != null) return preferred;
    }
    return IdeFocusTraversalPolicy.getPreferredFocusedComponent(myCenterPanel);
  }

  public void dispose() {
    super.dispose();
    myConfigurable.disposeUIResources();
    myConfigurable = null;
  }
}