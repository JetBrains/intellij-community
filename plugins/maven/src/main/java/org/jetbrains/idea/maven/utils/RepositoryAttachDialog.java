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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AsyncProcessIcon;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.*;
import java.util.List;

public class RepositoryAttachDialog extends DialogWrapper {

  @NonNls private static final String PROPERTY_ATTACH_JAVADOC = "Repository.Attach.JavaDocs";
  @NonNls private static final String PROPERTY_ATTACH_SOURCES = "Repository.Attach.Sources";

  private JLabel myInfoLabel;
  private JCheckBox myJavaDocCheckBox;
  private JCheckBox mySourcesCheckBox;
  private final Project myProject;
  private final boolean myManaged;
  private AsyncProcessIcon myProgressIcon;
  private ComboboxWithBrowseButton myComboComponent;
  private JPanel myPanel;
  private JLabel myCaptionLabel;
  private final THashMap<String, Pair<MavenArtifactInfo, MavenRepositoryInfo>> myCoordinates
    = new THashMap<String, Pair<MavenArtifactInfo, MavenRepositoryInfo>>();
  private final Map<String, MavenRepositoryInfo> myRepositories = new TreeMap<String, MavenRepositoryInfo>();
  private final ArrayList<String> myShownItems = new ArrayList<String>();
  private final JComboBox myCombobox;

  private TextFieldWithBrowseButton myDirectoryField;
  private String myFilterString;

  public RepositoryAttachDialog(Project project, boolean managed) {
    super(project, true);
    myProject = project;
    myManaged = managed;
    myProgressIcon.suspend();
    myCaptionLabel.setText("Enter keywords to search by, class name or Maven coordinates,\n" +
                           "i.e. 'springframework', 'Logger' or 'org.hibernate:hibernate-core:3.5.0.GA':");
    myCaptionLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
    myCaptionLabel.setUI(new MultiLineLabelUI());
    myInfoLabel.setUI(new MultiLineLabelUI());
    myInfoLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
    myInfoLabel.setPreferredSize(new Dimension(myInfoLabel.getFontMetrics(myInfoLabel.getFont()).stringWidth("Showing: 1000"), myInfoLabel.getPreferredSize().height));

    myComboComponent.setButtonIcon(IconLoader.findIcon("/actions/menu-find.png"));
    myComboComponent.getButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        performSearch();
      }
    });
    myCombobox = myComboComponent.getComboBox();
    myCombobox.setModel(new CollectionComboBoxModel(myShownItems, null));
    myCombobox.setEditable(true);
    final JTextField textField = (JTextField)myCombobox.getEditor().getEditorComponent();
    textField.setColumns(50);
    textField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (myProgressIcon.isDisposed()) return;
            updateComboboxSelection(false);
          }
        });
      }
    });
    textField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        final boolean popupVisible = myCombobox.isPopupVisible();
        if (e.getKeyCode() == KeyEvent.VK_ENTER && e.getModifiers() == 0) {
          //if (true) return;
          if (popupVisible && !myCoordinates.isEmpty()) {
            final String item = (String)myCombobox.getSelectedItem();
            if (StringUtil.isNotEmpty(item)) {
              ((JTextField)myCombobox.getEditor().getEditorComponent()).setText(item);
            }
          }
          else if (!popupVisible || myCoordinates.isEmpty()) {
            if (performSearch()) {
              e.consume();
            }
          }
        }
      }
    });
    if (!myManaged) {
      if (myProject != null && !myProject.isDefault()) {
        final VirtualFile baseDir = myProject.getBaseDir();
        if (baseDir != null) {
          myDirectoryField.setText(FileUtil.toSystemDependentName(baseDir.getPath() + "/lib"));
        }
      }
      myDirectoryField.addBrowseFolderListener(ProjectBundle.message("file.chooser.directory.for.downloaded.libraries.title"),
                                               ProjectBundle.message("file.chooser.directory.for.downloaded.libraries.description"), null,
                                               FileChooserDescriptorFactory.createSingleFolderDescriptor());
    }
    else {
      myDirectoryField.setVisible(false);
    }
    final PropertiesComponent storage = PropertiesComponent.getInstance(myProject);
    myJavaDocCheckBox.setSelected(storage.isValueSet(PROPERTY_ATTACH_JAVADOC) && storage.isTrueValue(PROPERTY_ATTACH_JAVADOC));
    mySourcesCheckBox.setSelected(storage.isValueSet(PROPERTY_ATTACH_SOURCES) && storage.isTrueValue(PROPERTY_ATTACH_SOURCES));
    updateInfoLabel();
    init();
  }

  public boolean getAttachJavaDoc() {
    return myJavaDocCheckBox.isSelected();
  }

  public boolean getAttachSources() {
    return mySourcesCheckBox.isSelected();
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
    final JTextComponent field = (JTextComponent)myCombobox.getEditor().getEditorComponent();
    final String prefix = field.getText();
    final int caret = field.getCaretPosition();
    myFilterString = prefix.toUpperCase();

    if (!force && myFilterString.equals(prevFilter)) return;
    myShownItems.clear();
    final boolean itemSelected = Comparing.equal(myCombobox.getSelectedItem(), prefix);
    if (itemSelected) {
      myShownItems.addAll(myCoordinates.keySet());
    }
    else {
      final String[] parts = myFilterString.split(" ");
      main:
      for (String coordinate : myCoordinates.keySet()) {
        final String candidate = coordinate.toUpperCase();
        for (String part : parts) {
          if (!candidate.contains(part)) continue main;
        }
        myShownItems.add(coordinate);
      }
      if (myShownItems.isEmpty()) {
        myShownItems.addAll(myCoordinates.keySet());
      }
      myCombobox.setSelectedItem(null);
    }
    Collections.sort(myShownItems);
    ((CollectionComboBoxModel)myCombobox.getModel()).update();
    field.setText(prefix);
    field.setCaretPosition(caret);
    updateInfoLabel();
    if (myCombobox.getEditor().getEditorComponent().hasFocus()) {
      myCombobox.setPopupVisible(!myShownItems.isEmpty() && !itemSelected);
    }
  }

  private boolean performSearch() {
    final String text = getCoordinateText();
    if (myCoordinates.contains(text)) return false;
    if (myProgressIcon.isRunning()) return false;
    myProgressIcon.resume();
    RepositoryAttachHandler.searchArtifacts(myProject, text, new PairProcessor<Collection<Pair<MavenArtifactInfo, MavenRepositoryInfo>>, Boolean>() {
      public boolean process(Collection<Pair<MavenArtifactInfo, MavenRepositoryInfo>> artifacts, Boolean tooMany) {
        if (myProgressIcon.isDisposed()) return true;
        myProgressIcon.suspend();
        final int prevSize = myCoordinates.size();
        for (Pair<MavenArtifactInfo, MavenRepositoryInfo> each : artifacts) {
          myCoordinates.put(each.first.getGroupId() + ":" + each.first.getArtifactId() + ":" + each.first.getVersion(), each);
          if (each.second != null && !myRepositories.containsKey(each.second.getUrl())) {
            myRepositories.put(each.second.getUrl(), each.second);
          }
        }
        if (Boolean.TRUE.equals(tooMany)) {
          final Point point = new Point(myCombobox.getWidth() / 2, 0);
          JBPopupFactory.getInstance().createHtmlTextBalloonBuilder("Too many results found, please refine your query.", MessageType.WARNING, null).
            setHideOnClickOutside(true).
            createBalloon().show(new RelativePoint(myCombobox, point), Balloon.Position.above);
        }
        updateComboboxSelection(prevSize != myCoordinates.size());
        return true;
      }
    });
    return true;
  }

  private void updateInfoLabel() {
    myInfoLabel.setText("Found: " + myCoordinates.size()+ "\nShowing: " + myCombobox.getModel().getSize());
  }

  @Override
  public boolean isOKActionEnabled() {
    return true;
  }

  @Override
  protected void doOKAction() {
    if (!isOKActionEnabled()) return;
    String errorText = null;
    JComponent errorComponent = null;
    if (!isValidCoordinateSelected()) {
      errorComponent = myCombobox;
      errorText = "Please enter valid coordinate, discover it or select one from the list";
    }
    else if (!myManaged) {
      final File dir = new File(myDirectoryField.getText());
      if (!dir.exists() && !dir.mkdirs() || !dir.isDirectory()) {
        errorComponent = myDirectoryField.getTextField();
        errorText = "Please enter valid library files path";
      }
    }
    if (errorText != null && errorComponent != null) {
      final Point point = new Point(errorComponent.getWidth() / 2, 0);
      JBPopupFactory.getInstance()
        .createHtmlTextBalloonBuilder(errorText, MessageType.WARNING, null).
        setHideOnClickOutside(false).setHideOnKeyOutside(true).
        createBalloon().show(new RelativePoint(errorComponent, point), Balloon.Position.above);
      IdeFocusManager.findInstance().requestFocus(errorComponent, true);
    }
    else {
      super.doOKAction();
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  protected JComponent createNorthPanel() {
    return myPanel;
  }


  @Override
  protected void dispose() {
    Disposer.dispose(myProgressIcon);
    final PropertiesComponent storage = PropertiesComponent.getInstance(myProject);
    storage.setValue(PROPERTY_ATTACH_JAVADOC, String.valueOf(myJavaDocCheckBox.isSelected()));
    storage.setValue(PROPERTY_ATTACH_SOURCES, String.valueOf(mySourcesCheckBox.isSelected()));
    super.dispose();
  }

  @Override
  protected String getDimensionServiceKey() {
    return RepositoryAttachDialog.class.getName();
  }

  @NotNull
  public List<MavenRepositoryInfo> getRepositories() {
    final Pair<MavenArtifactInfo, MavenRepositoryInfo> artifactAndRepo = myCoordinates.get(getCoordinateText());
    final MavenRepositoryInfo repository = artifactAndRepo == null ? null : artifactAndRepo.second;
    return repository != null ? Collections.singletonList(repository) : ContainerUtil.findAll(myRepositories.values(), Condition.NOT_NULL);
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

  private void createUIComponents() {
    myProgressIcon = new AsyncProcessIcon("Progress");
  }
}
