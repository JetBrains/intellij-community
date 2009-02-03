package com.intellij.facet.impl.ui;

import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportConfigurable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dmitry Avdeev
*/
public class VersionConfigurable extends FrameworkSupportConfigurable {
  private JComboBox myComboBox;
  private final VersionedFrameworkSupportProvider myFrameworkSupportProvider;

  public VersionConfigurable(final VersionedFrameworkSupportProvider frameworkSupportProvider, String[] versions, String defaultVersion) {
    myFrameworkSupportProvider = frameworkSupportProvider;
    if (versions.length > 0) {
      if (defaultVersion == null) {
        defaultVersion = versions[versions.length - 1];
      }
      myComboBox = new JComboBox();
      String maxValue = "";
      for (String version : versions) {
        myComboBox.addItem(version);
        FontMetrics fontMetrics = myComboBox.getFontMetrics(myComboBox.getFont());
        if (fontMetrics.stringWidth(version) > fontMetrics.stringWidth(maxValue)) {
          maxValue = version;
        }
      }
      myComboBox.setPrototypeDisplayValue(maxValue + "_");
      myComboBox.setSelectedItem(defaultVersion);
    }
  }

  public JComponent getComponent() {
    return myComboBox;
  }

  @NotNull
  public LibraryInfo[] getLibraries() {
    return myFrameworkSupportProvider.getLibraries(getSelectedVersion());
  }

  @NonNls
  @NotNull
  public String getLibraryName() {
    return myFrameworkSupportProvider.getLibraryName(getSelectedVersion());
  }

  public void addSupport(final Module module, final ModifiableRootModel rootModel, final @Nullable Library library) {
    myFrameworkSupportProvider.addSupport(module, rootModel, getSelectedVersion(), library);
  }

  public String getSelectedVersion() {
    String version = null;
    if (myComboBox != null) {
      version = myComboBox.getSelectedItem().toString();
    }
    return version;
  }

}
