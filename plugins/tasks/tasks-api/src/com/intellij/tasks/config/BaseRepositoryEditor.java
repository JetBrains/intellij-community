/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tasks.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.Consumer;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author Dmitry Avdeev
 */
public class BaseRepositoryEditor<T extends BaseRepository> extends TaskRepositoryEditor implements PanelWithAnchor {

  protected JBLabel myUrlLabel;
  protected JTextField myURLText;
  protected JTextField myUserNameText;
  protected JBLabel myUsernameLabel;
  protected JCheckBox myShareUrlCheckBox;
  protected JPasswordField myPasswordText;
  protected JBLabel myPasswordLabel;

  protected JButton myTestButton;
  private JPanel myPanel;
  private JBCheckBox myUseProxy;
  private JButton myProxySettingsButton;
  protected JCheckBox myUseHttpAuthenticationCheckBox;

  protected JPanel myCustomPanel;
  private JBCheckBox myAddCommitMessage;
  private JBLabel myComment;
  private JPanel myEditorPanel;
  protected JBCheckBox myLoginAnonymouslyJBCheckBox;
  protected JBTabbedPane myTabbedPane;
  private JTextPane myAdvertiser;

  private boolean myApplying;
  protected Project myProject;
  protected final T myRepository;
  private final Consumer<T> myChangeListener;
  private final Document myDocument;
  private final Editor myEditor;
  private JComponent myAnchor;

  public BaseRepositoryEditor(final Project project, final T repository, Consumer<T> changeListener) {
    myProject = project;
    myRepository = repository;
    myChangeListener = changeListener;

    myTestButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        afterTestConnection(TaskManager.getManager(project).testConnection(repository));
      }
    });

    myProxySettingsButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        HttpConfigurable.editConfigurable(myPanel);
        enableButtons();
        doApply();
      }
    });

    myURLText.setText(repository.getUrl());
    myUserNameText.setText(repository.getUsername());
    myPasswordText.setText(repository.getPassword());
    myShareUrlCheckBox.setSelected(repository.isShared());
    myUseProxy.setSelected(repository.isUseProxy());

    myUseHttpAuthenticationCheckBox.setSelected(repository.isUseHttpAuthentication());
    myUseHttpAuthenticationCheckBox.setVisible(repository.isSupported(TaskRepository.BASIC_HTTP_AUTHORIZATION));

    myLoginAnonymouslyJBCheckBox.setVisible(repository.isSupported(TaskRepository.LOGIN_ANONYMOUSLY));
    myLoginAnonymouslyJBCheckBox.setSelected(repository.isLoginAnonymously());
    myLoginAnonymouslyJBCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        loginAnonymouslyChanged(!myLoginAnonymouslyJBCheckBox.isSelected());
      }
    });

    myAddCommitMessage.setSelected(repository.isShouldFormatCommitMessage());
    myDocument = EditorFactory.getInstance().createDocument(repository.getCommitMessageFormat());
    myEditor = EditorFactory.getInstance().createEditor(myDocument);
    myEditor.getSettings().setCaretRowShown(false);
    myEditorPanel.add(myEditor.getComponent(), BorderLayout.CENTER);
    myComment.setText("Available placeholders: " + repository.getComment());
    String advertiser = repository.getRepositoryType().getAdvertiser();
    if (advertiser != null) {
      Messages.installHyperlinkSupport(myAdvertiser);
      myAdvertiser.setText(advertiser);
    }
    else {
      myAdvertiser.setVisible(false);
    }

    installListener(myAddCommitMessage);
    installListener(myDocument);

    installListener(myURLText);
    installListener(myUserNameText);
    installListener(myPasswordText);

    installListener(myShareUrlCheckBox);
    installListener(myUseProxy);
    installListener(myUseHttpAuthenticationCheckBox);
    installListener(myLoginAnonymouslyJBCheckBox);

    enableButtons();
    enableEditor();

    JComponent customPanel = createCustomPanel();
    if (customPanel != null) {
      myCustomPanel.add(customPanel, BorderLayout.CENTER);
    }

    setAnchor(myUseProxy);
    loginAnonymouslyChanged(!myLoginAnonymouslyJBCheckBox.isSelected());
  }


  protected final void updateCustomPanel() {
    myCustomPanel.removeAll();
    JComponent customPanel = createCustomPanel();
    if (customPanel != null) {
      myCustomPanel.add(customPanel, BorderLayout.CENTER);
    }
    myCustomPanel.repaint();
  }

  private void loginAnonymouslyChanged(boolean enabled) {
    myUsernameLabel.setEnabled(enabled);
    myUserNameText.setEnabled(enabled);
    myPasswordLabel.setEnabled(enabled);
    myPasswordText.setEnabled(enabled);
    myUseHttpAuthenticationCheckBox.setEnabled(enabled);
  }

  @Nullable
  protected JComponent createCustomPanel() {
    return null;
  }

  protected void afterTestConnection(final boolean connectionSuccessful) {
  }

  protected void enableButtons() {
    myUseProxy.setEnabled(HttpConfigurable.getInstance().USE_HTTP_PROXY);
    if (!HttpConfigurable.getInstance().USE_HTTP_PROXY) {
      myUseProxy.setSelected(false);
    }
  }

  protected void installListener(JCheckBox checkBox) {
    checkBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        doApply();
      }
    });
  }

  protected void installListener(JTextField textField) {
    textField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            doApply();
          }
        });
      }
    });
  }

  protected void installListener(JComboBox comboBox) {
    comboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(final ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          doApply();
        }
      }
    });
  }

  protected void installListener(final Document document) {
    document.addDocumentListener(new com.intellij.openapi.editor.event.DocumentAdapter() {
      @Override
      public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
        doApply();
      }
    });
  }

  protected void installListener(EditorTextField editor) {
    installListener(editor.getDocument());
  }

  protected void doApply() {
    if (!myApplying) {
      try {
        myApplying = true;
        apply();
        enableEditor();
      }
      finally {
        myApplying = false;
      }
    }
  }

  private void enableEditor() {
    boolean selected = myAddCommitMessage.isSelected();
    UIUtil.setEnabled(myEditorPanel, selected, true);
    ((EditorEx)myEditor).setRendererMode(!selected);
  }

  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myURLText;
  }

  @Override
  public void dispose() {
    EditorFactory.getInstance().releaseEditor(myEditor);
  }

  public void apply() {

    myRepository.setUrl(myURLText.getText().trim());
    myRepository.setUsername(myUserNameText.getText().trim());
    //noinspection deprecation
    myRepository.setPassword(myPasswordText.getText());
    myRepository.setShared(myShareUrlCheckBox.isSelected());
    myRepository.setUseProxy(myUseProxy.isSelected());
    myRepository.setUseHttpAuthentication(myUseHttpAuthenticationCheckBox.isSelected());
    myRepository.setLoginAnonymously(myLoginAnonymouslyJBCheckBox.isSelected());

    myRepository.setShouldFormatCommitMessage(myAddCommitMessage.isSelected());
    myRepository.setCommitMessageFormat(myDocument.getText());

    myChangeListener.consume(myRepository);
  }

  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public void setAnchor(@Nullable final JComponent anchor) {
    myAnchor = anchor;
    myUrlLabel.setAnchor(anchor);
    myUsernameLabel.setAnchor(anchor);
    myPasswordLabel.setAnchor(anchor);
    myUseProxy.setAnchor(anchor);
  }
}
