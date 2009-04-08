package com.intellij.facet.impl.ui.libraries.versions;

import com.intellij.facet.ui.libraries.FacetLibrariesValidator;
import com.intellij.facet.ui.libraries.FacetLibrariesValidatorDescription;
import com.intellij.facet.ui.libraries.JarVersionDetectionUtil;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class VersionsComponent {
  private JPanel myMainPanel;
  private static String UNKNOWN_RI_NAME = "Unknown";

  @Nullable private Module myModule;
  private FacetLibrariesValidator myValidator;

  private ButtonGroup myButtonGroup = new ButtonGroup();

  private Map<String, Pair<JRadioButton, JComboBox>> myButtons = new HashMap<String, Pair<JRadioButton, JComboBox>>();

  public VersionsComponent(@Nullable final Module module, FacetLibrariesValidator validator) {
    myModule = module;
    myValidator = validator;
  }

  public JPanel getJComponent() {
    if (myMainPanel == null) {
      init();
    }
    return myMainPanel;
  }

  private void init() {
    myMainPanel = new JPanel(new GridBagLayout());

    Set<String> referenceImplementations = getRIs();

    if (referenceImplementations.size() == 1) {
      String ri = referenceImplementations.iterator().next();
      addSingletonReferenceImplementationUI(ri);
    }
    else {
      LibraryVersionInfo currentVersion = null;

      for (String ri : referenceImplementations) {
        addMultipleReferenceImplementationUI(ri);

        if (currentVersion == null) {
          currentVersion = getCurrentVersion(ri);
        }
      }

      if (currentVersion != null) {
        Pair<JRadioButton, JComboBox> currentPair = myButtons.get(currentVersion.getRI());
        if (currentPair != null) {
          currentPair.first.setSelected(true);
          currentPair.second.setSelectedItem(currentVersion);
          for (Pair<JRadioButton, JComboBox> buttonsPair : myButtons.values()) {
            if (buttonsPair != currentPair) {
              buttonsPair.second.setEnabled(false);
            }
          }
        }
      }
    }
  }

  @Nullable
  protected String getFacetDetectionClass(@NotNull String currentRI) {
    return null;
  }

  @NotNull
  protected abstract Map<LibraryVersionInfo, List<LibraryInfo>> getLibraries();

  @Nullable
  private LibraryVersionInfo getCurrentVersion(@NotNull String currentRI) {
    String detectionClass = getFacetDetectionClass(currentRI);
    if (detectionClass != null && myModule != null) {
      final String version = JarVersionDetectionUtil.detectJarVersion(detectionClass, myModule);
      if (version != null) {
        for (LibraryVersionInfo info : getLibraries().keySet()) {
          if (version.equals(info.getVersion())) {
            return info;
          }
        }
      }
    }

    return null;
  }

  private List<LibraryVersionInfo> getSupportedVersions(@NotNull String ri) {
    List<LibraryVersionInfo> versions = new ArrayList<LibraryVersionInfo>();
    for (Map.Entry<LibraryVersionInfo, List<LibraryInfo>> entry : getLibraries().entrySet()) {
      if (ri.equals(entry.getKey().getRI())) {
        versions.add(entry.getKey());
      }
    }

    return versions;
  }

  private void addSingletonReferenceImplementationUI(@NotNull final String ri) {
    JComboBox comboBox = createComboBox(ri);
    addToPanel(new JLabel(ri), comboBox);
    LibraryVersionInfo version = getCurrentVersion(ri);
    if (version != null) {
      comboBox.setSelectedItem(version);
    }
  }

  private void addMultipleReferenceImplementationUI(@NotNull final String ri) {
    final JRadioButton radioButton = createRadioButton(ri);
    final JComboBox comboBox = createComboBox(ri);

    addToPanel(radioButton, comboBox);

    myButtons.put(ri, new Pair<JRadioButton, JComboBox>(radioButton, comboBox));
    myButtonGroup.add(radioButton);
  }

  private void addToPanel(@NotNull JComponent first, @NotNull JComponent second) {
    myMainPanel.add(first, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.LINE_START,
                                                  GridBagConstraints.BOTH, new Insets(2, 2, 2, 2), 0, 0));
    myMainPanel.add(second,
                    new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.LINE_END, GridBagConstraints.BOTH,
                                           new Insets(2, 2, 2, 2), 0, 0));
  }

  private JRadioButton createRadioButton(final String ri) {
    final JRadioButton radioButton = new JRadioButton(ri);
    radioButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        for (Pair<JRadioButton, JComboBox> pair : myButtons.values()) {
          if (pair.getFirst().equals(radioButton)) {
            JComboBox comboBox = pair.second;
            comboBox.setEnabled(true);

            LibraryVersionInfo currentVersion = getCurrentVersion(ri);
            if (currentVersion != null) {
              comboBox.setSelectedItem(currentVersion);
            }
            else {
              comboBox.setSelectedItem(getLastElement(getSupportedVersions(ri)));
            }
          }
          else {
            pair.second.setEnabled(false);
          }
        }
      }
    });
    return radioButton;
  }

  private JComboBox createComboBox(String ri) {
    final JComboBox comboBox = new JComboBox();

    List<LibraryVersionInfo> versions = getSupportedVersions(ri);
    comboBox.setModel(new CollectionComboBoxModel(versions, null));

    comboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final LibraryVersionInfo versionInfo = getSelectedVersion(comboBox);

        if (versionInfo != null) {
          myValidator.setDescription(new FacetLibrariesValidatorDescription(versionInfo.getVersion()));
          myValidator.setRequiredLibraries(getRequiredLibraries(versionInfo));
        }
      }
    });

    return comboBox;
  }

  @Nullable
  private static LibraryVersionInfo getLastElement(List<LibraryVersionInfo> versions) {
    return versions.size() > 0 ? versions.get(versions.size() - 1) : null;
  }

  private LibraryInfo[] getRequiredLibraries(LibraryVersionInfo versionInfo) {
    List<LibraryInfo> libraryInfos = getLibraries().get(versionInfo);
    return libraryInfos.toArray(new LibraryInfo[libraryInfos.size()]);
  }

  @Nullable
  private static LibraryVersionInfo getSelectedVersion(@NotNull JComboBox comboBox) {
    final Object version = comboBox.getModel().getSelectedItem();
    return version instanceof LibraryVersionInfo ? (LibraryVersionInfo)version : null;
  }


  public FacetLibrariesValidator getValidator() {
    return myValidator;
  }

  @Nullable
  public Module getModule() {
    return myModule;
  }

  public Set<String> getRIs() {
    Set<String> ris = new HashSet<String>();
    for (LibraryVersionInfo info : getLibraries().keySet()) {
      String ri = info.getRI();
      if (!StringUtil.isEmptyOrSpaces(ri)) {
        ris.add(ri);
      }
      else {
        ris.add(UNKNOWN_RI_NAME);
      }
    }
    return ris;
  }
}
