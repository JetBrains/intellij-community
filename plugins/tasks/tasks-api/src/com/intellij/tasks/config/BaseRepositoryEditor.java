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
import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.Consumer;
import com.intellij.util.net.HttpConfigurable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Dmitry Avdeev
 */
public class BaseRepositoryEditor<T extends BaseRepository> extends TaskRepositoryEditor {

  protected JLabel myUrlLabel;
  protected JTextField myURLText;
  protected JTextField myUserNameText;
  protected JLabel myUsernameLabel;  
  protected JCheckBox myShareURL;
  protected JPasswordField myPasswordText;
  protected JLabel myPasswordLabel;

  private JButton myTestButton;
  private JPanel myPanel;
  private JCheckBox myUseProxy;
  private JButton myProxySettingsButton;
  protected JCheckBox myUseHTTPAuthentication;

  protected JPanel myCustomPanel;
  protected JPanel myCustomLabel;

  private boolean myApplying;
  protected final T myRepository;
  private final Consumer<T> myChangeListener;

  public BaseRepositoryEditor(final Project project, final T repository, Consumer<T> changeListener) {
    myRepository = repository;
    myChangeListener = changeListener;

    myTestButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        TaskManager.getManager(project).testConnection(repository);
      }
    });

    myProxySettingsButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        HttpConfigurable.editConfigurable(myPanel);
        enableButtons();
      }
    });

    myURLText.setText(repository.getUrl());
    myUserNameText.setText(repository.getUsername());
    myPasswordText.setText(repository.getPassword());
    myShareURL.setSelected(repository.isShared());
    myUseProxy.setSelected(repository.isUseProxy());

    myUseHTTPAuthentication.setSelected(repository.isUseHttpAuthentication());
    myUseHTTPAuthentication.setVisible(repository.getRepositoryType().isSupported(TaskRepositoryType.BASIC_HTTP_AUTHORIZATION));

    installListener(myURLText);
    installListener(myUserNameText);
    installListener(myPasswordText);

    installListener(myShareURL);
    installListener(myUseProxy);
    installListener(myUseHTTPAuthentication);

    enableButtons();
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

  private void doApply() {
    if (!myApplying) {
      try {
        myApplying = true;
        apply();
      }
      finally {
        myApplying = false;
      }
    }
  }

  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myURLText;
  }

  public void apply() {

    myRepository.setUrl(myURLText.getText().trim());
    myRepository.setUsername(myUserNameText.getText().trim());
    //noinspection deprecation
    myRepository.setPassword(myPasswordText.getText());
    myRepository.setShared(myShareURL.isSelected());
    myRepository.setUseProxy(myUseProxy.isSelected());
    myRepository.setUseHttpAuthentication(myUseHTTPAuthentication.isSelected());

    myChangeListener.consume(myRepository);
  }
}
