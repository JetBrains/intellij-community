package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import org.jetbrains.idea.svn.SvnBundle;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AddRepositoryLocationDialog extends DialogWrapper {
  private final List<String> myPreviousLocations;
  private JComboBox myCombo;
  private String mySelected;
  private JTextField myComboField;

  public AddRepositoryLocationDialog(final Project project, final List<String> values) {
    super(project, true);
    myPreviousLocations = new ArrayList<String>(values);
    Collections.sort(myPreviousLocations);

    setTitle(getTitle());
    init();
    myComboField.setText(initText());
  }

  @Override
  public String getTitle() {
    return SvnBundle.message("repository.browser.add.location.title");
  }

  protected String initText() {
    return "http://";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCombo;
  }

  protected JComponent createCenterPanel() {
    final JLabel selectText = new JLabel(SvnBundle.message("repository.browser.add.location.prompt"));
    selectText.setUI(new MultiLineLabelUI());

    final JPanel mainPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints gb =
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0);

    mainPanel.add(selectText, gb);

    ++ gb.gridy;

    myCombo = new JComboBox(myPreviousLocations.toArray(new Object[myPreviousLocations.size()])) {
      @Override
      public void processKeyEvent(final KeyEvent e) {
        super.processKeyEvent(e);
        validateMe();
      }
    };
    myCombo.setEditable(true);
    myCombo.setSelectedIndex(0);
    myCombo.setMinimumSize(new Dimension(250, 20));

    mainPanel.add(myCombo, gb);

    myComboField = (JTextField)myCombo.getEditor().getEditorComponent();
    myComboField.addInputMethodListener(new InputMethodListener() {
      public void inputMethodTextChanged(final InputMethodEvent event) {
        validateMe();
      }

      public void caretPositionChanged(final InputMethodEvent event) {
        validateMe();
      }
    });
    myComboField.addKeyListener(new KeyListener() {
      public void keyTyped(final KeyEvent e) {
        validateMe();
      }

      public void keyPressed(final KeyEvent e) {
        validateMe();
      }

      public void keyReleased(final KeyEvent e) {
        validateMe();
      }
    });

    myCombo.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        validateMe();
      }
    });
    validateMe();

    final JPanel wrapper = new JPanel(new GridBagLayout());
    wrapper.add(mainPanel, new GridBagConstraints(0,0,1,1,1,1,GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                  new Insets(0,0,0,0), 0,0));
    return wrapper;
  }

  private void validateMe() {
    final String inputString = myComboField.getText();
    setOKActionEnabled(urlValid(inputString));
  }

  private boolean urlValid(final String inputString) {
    if (inputString == null) {
      return false;
    }
    try {
      final SVNURL svnurl = SVNURL.parseURIEncoded(inputString);
      return svnurl != null;
    } catch (SVNException e) {
      //
    }
    return false;
  }

  @Override
  protected void doOKAction() {
    mySelected = myComboField.getText();
    super.doOKAction();
  }

  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  public String getSelected() {
    return mySelected;
  }
}
