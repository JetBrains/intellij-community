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
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.Icons;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.containers.hash.*;
import com.intellij.util.ui.AsyncProcessIcon;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.facade.nexus.ArtifactType;
import org.jetbrains.idea.maven.facade.nexus.RepositoryType;
import org.jetbrains.idea.maven.facade.remote.MavenFacade;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.*;
import java.util.List;

public class RepositoryAttachDialog extends DialogWrapper {
  private static final String DEFAULT_REPOSITORY = "<default>";
  private final JLabel myInfoLabel;
  private final Project myProject;
  private final boolean myManaged;
  private final AsyncProcessIcon myProgressIcon;
  private JComboBox myCombobox;
  private String myFilterString;
  private THashMap<String, ArtifactType> myCoordinates = new THashMap<String, ArtifactType>();
  private ArrayList<String> myShownItems = new ArrayList<String>();

  private TextFieldWithBrowseButton myDirectoryField;
  private JComboBox myRepositoryUrl;
  private final Map<String, MavenFacade.Repository> myRepositories = new TreeMap<String, MavenFacade.Repository>();

  public RepositoryAttachDialog(Project project, boolean managed) {
    super(project, true);
    myProject = project;
    myManaged = managed;
    myProgressIcon = new AsyncProcessIcon("Progress");
    myProgressIcon.setVisible(false);
    myProgressIcon.suspend();
    myInfoLabel = new JLabel("");
    myCombobox = new JComboBox(new CollectionComboBoxModel(myShownItems, null));
    myCombobox.setEditable(true);
    ((JTextField)myCombobox.getEditor().getEditorComponent()).setColumns(50);
    myCombobox.getEditor().getEditorComponent().addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        final boolean popupVisible = myCombobox.isPopupVisible();
        if (e.getKeyCode() == KeyEvent.VK_ENTER && e.getModifiers() == 0) {
          //if (true) return;
          if (!popupVisible) {
            if (performSearch()) {
              e.consume();
            }
          }
        }
        else if (e.getKeyCode() == KeyEvent.VK_UP || e.getKeyCode() == KeyEvent.VK_DOWN ||
                 e.getKeyCode() == KeyEvent.VK_PAGE_UP || e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
          if (popupVisible) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                if (myProgressIcon.isDisposed()) return;
                myCombobox.getEditor().setItem(myCombobox.getSelectedItem());
              }
            });
          }
        }
        else {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              if (myProgressIcon.isDisposed()) return;
              updateComboboxSelection(false);
            }
          });
        }
      }
    });
    updateInfoLabel();
    init();
  }

  public String getDirectoryPath() {
    return myDirectoryField.getText();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCombobox;
  }

  private void updateComboboxSelection(boolean force) {
    final String prevFilter = myFilterString;
    final String prefix = getCoordinateText();
    myFilterString = prefix.toUpperCase();
    if (!force && myFilterString.equals(prevFilter)) return;
    myShownItems.clear();
    for (String coordinate : myCoordinates.keySet()) {
      if (coordinate.toUpperCase().contains(myFilterString)) {
        myShownItems.add(coordinate);
      }
    }
    Collections.sort(myShownItems);
    ((CollectionComboBoxModel)myCombobox.getModel()).update();
    setInputText(prefix);
    if (myCombobox.getEditor().getEditorComponent().hasFocus()) {
      myCombobox.setPopupVisible(!myShownItems.isEmpty());
    }
    updateInfoLabel();
  }

  private boolean performSearch() {
    final String text = getCoordinateText();
    if (myCoordinates.contains(text)) return false;
    if (myProgressIcon.isVisible()) return false;
    myProgressIcon.setVisible(true);
    myProgressIcon.resume();
    RepositoryAttachHandler.searchArtifacts(myProject, text, new PairProcessor<Collection<ArtifactType>, Collection<RepositoryType>>() {
      public boolean process(Collection<ArtifactType> artifactTypes, Collection<RepositoryType> repositoryTypes) {
        if (myProgressIcon.isDisposed()) return true;
        myProgressIcon.suspend();
        myProgressIcon.setVisible(false);
        for (ArtifactType artifactType : artifactTypes) {
          myCoordinates.put(artifactType.getGroupId() + ":" + artifactType.getArtifactId() + ":" + artifactType.getVersion(), artifactType);
        }
        for (RepositoryType repositoryType : repositoryTypes) {
          if (!myRepositories.containsKey(repositoryType.getContentResourceURI())) {
            myRepositories.put(
              repositoryType.getContentResourceURI(),
              new MavenFacade.Repository(repositoryType.getId(), repositoryType.getContentResourceURI(), "default"));
          }
        }
        myRepositoryUrl.setModel(new CollectionComboBoxModel(new ArrayList<String>(myRepositories.keySet()), myRepositoryUrl.getEditor().getItem()));
        updateComboboxSelection(true);
        return true;
      }
    });
    return true;
  }

  private void updateInfoLabel() {
    myInfoLabel.setText(myCombobox.getModel().getSize() +"/" + myCoordinates.size());
  }

  @Override
  public boolean isOKActionEnabled() {
    return true;
  }

  @Override
  protected void doOKAction() {
    if (!isOKActionEnabled()) return;
    if (!isValidCoordinateSelected()) {
      IdeFocusManager.findInstance().requestFocus(myCombobox, true);
      Messages.showErrorDialog("Please enter valid coordinate or select one from the list",
                               "Coordinate not specified");
      return;
    }
    if (!myManaged) {
      final File dir = new File(myDirectoryField.getText());
      if (!dir.exists() && !dir.mkdirs() || !dir.isDirectory()) {
        IdeFocusManager.findInstance().requestFocus(myDirectoryField.getChildComponent(), true);
        Messages.showErrorDialog("Please enter valid library files path",
                                 "Library files path not specified");
        return;
      }
    }
    super.doOKAction();
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new BorderLayout(15, 0));
    {
      JLabel iconLabel = new JLabel(Messages.getQuestionIcon());
      Container container = new Container();
      container.setLayout(new BorderLayout());
      container.add(iconLabel, BorderLayout.NORTH);
      panel.add(container, BorderLayout.WEST);
    }

    final ArrayList<JComponent> gridComponents = new ArrayList<JComponent>();
    {
      JPanel caption = new JPanel(new BorderLayout(15, 0));
      JLabel textLabel = new JLabel("Enter keywords or Maven coordinates: \ni.e. 'spring', 'jsf' or 'org.hibernate:hibernate-core:3.3.0.GA'");
      textLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
      textLabel.setUI(new MultiLineLabelUI());
      caption.add(textLabel, BorderLayout.WEST);
      final JPanel infoPanel = new JPanel(new BorderLayout());
      infoPanel.add(myInfoLabel, BorderLayout.WEST);
      infoPanel.add(myProgressIcon, BorderLayout.EAST);
      caption.add(infoPanel, BorderLayout.EAST);
      gridComponents.add(caption);

      final ComponentWithBrowseButton<JComboBox> coordComponent = new ComponentWithBrowseButton<JComboBox>(myCombobox, new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          performSearch();
        }
      });
      coordComponent.setButtonIcon(Icons.SYNCHRONIZE_ICON);
      gridComponents.add(coordComponent);

      final LabeledComponent<JComboBox> repository = new LabeledComponent<JComboBox>();
      repository.getLabel().setText("Repository URL:");
      myRepositories.put(DEFAULT_REPOSITORY, null);
      for (MavenFacade.Repository repo : RepositoryAttachHandler.getDefaultRepositories()) {
        myRepositories.put(repo.getUrl(), repo);
      }
      myRepositoryUrl = new JComboBox(new CollectionComboBoxModel(new ArrayList<String>(myRepositories.keySet()), DEFAULT_REPOSITORY));
      myRepositoryUrl.setEditable(true);
      repository.setComponent(myRepositoryUrl);
      gridComponents.add(repository);

      if (!myManaged) {
        myDirectoryField = new TextFieldWithBrowseButton();
        if (myProject != null && !myProject.isDefault()) {
          final VirtualFile baseDir = myProject.getBaseDir();
          if (baseDir != null) {
            myDirectoryField.setText(FileUtil.toSystemDependentName(baseDir.getPath()+"/lib"));
          }
        }
        myDirectoryField.addBrowseFolderListener(ProjectBundle.message("file.chooser.directory.for.downloaded.libraries.title"),
                                   ProjectBundle.message("file.chooser.directory.for.downloaded.libraries.description"), null,
                                   FileChooserDescriptorFactory.createSingleFolderDescriptor());

        final LabeledComponent<TextFieldWithBrowseButton> dirComponent = new LabeledComponent<TextFieldWithBrowseButton>();
        dirComponent.getLabel().setText("Store Library Files in: ");
        dirComponent.setComponent(myDirectoryField);
        gridComponents.add(dirComponent);
      }
    }
    JPanel messagePanel = new JPanel(new GridLayoutManager(gridComponents.size(), 1));
    for (int i = 0, gridComponentsSize = gridComponents.size(); i < gridComponentsSize; i++) {
      messagePanel.add(gridComponents.get(i), new GridConstraints(i, 0, 1, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK, 0,
                                                          null, null, null));
    }
    panel.add(messagePanel, BorderLayout.CENTER);

    return panel;
  }


  @Override
  protected void dispose() {
    Disposer.dispose(myProgressIcon);
    super.dispose();
  }

  @Override
  protected String getDimensionServiceKey() {
    return RepositoryAttachDialog.class.getName();
  }

  @NotNull
  public List<MavenFacade.Repository> getRepositories() {
    final String selectedRepository = (String)myRepositoryUrl.getEditor().getItem();
    if (selectedRepository != null && selectedRepository != DEFAULT_REPOSITORY && !myRepositories.containsKey(selectedRepository)) {
      return Collections.singletonList(new MavenFacade.Repository("custom", selectedRepository, "default"));
    }
    else {
      final ArtifactType artifact = myCoordinates.get(getCoordinateText());
      final MavenFacade.Repository repository =
        artifact != null && artifact.getResourceUri() != null ? myRepositories.get(artifact.getResourceUri()) : null;
      return repository != null? Collections.singletonList(repository) : new ArrayList<MavenFacade.Repository>(myRepositories.values());
    }
  }

  private boolean isValidCoordinateSelected() {
    final String text = getCoordinateText();
    if (myCombobox.getModel().getSelectedItem() == null) return false;
    return text.split(":").length == 3;
  }

  public String getCoordinateText() {
    final JTextField field = (JTextField)myCombobox.getEditor().getEditorComponent();
    return field.getText();
  }

  private void setInputText(String text) {
    final JTextField field = (JTextField)myCombobox.getEditor().getEditorComponent();
    field.setText(text);
  }

}
