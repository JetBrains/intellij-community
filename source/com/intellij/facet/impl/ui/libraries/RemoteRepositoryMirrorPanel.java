package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.ui.libraries.RemoteRepositoryInfo;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import javax.swing.border.TitledBorder;

/**
 * @author nik
*/
public class RemoteRepositoryMirrorPanel {
  private JPanel myPanel;
  private JComboBox myMirrorComboBox;
  private final RemoteRepositoryInfo myRemoteRepository;

  public RemoteRepositoryMirrorPanel(final RemoteRepositoryInfo remoteRepository, final LibraryDownloadingMirrorsMap mirrorsMap) {
    myRemoteRepository = remoteRepository;
    String title = ProjectBundle.message("group.title.select.repository.0", remoteRepository.getPresentableName());
    TitledBorder titledBorder = IdeBorderFactory.createTitledBorder(title);
    myPanel.setBorder(BorderFactory.createCompoundBorder(titledBorder, IdeBorderFactory.createEmptyBorder(5, 5, 5, 5)));
    for (String mirror : remoteRepository.getMirrors()) {
      myMirrorComboBox.addItem(mirror);
    }
    myMirrorComboBox.setSelectedItem(mirrorsMap.getSelectedMirror(remoteRepository));
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public RemoteRepositoryInfo getRemoteRepository() {
    return myRemoteRepository;
  }

  public String getSelectedMirror() {
    return (String)myMirrorComboBox.getSelectedItem();
  }
}
