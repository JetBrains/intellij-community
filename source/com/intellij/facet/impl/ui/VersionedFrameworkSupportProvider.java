/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import com.intellij.ide.util.newProjectWizard.FrameworkSupportConfigurable;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.facet.ui.libraries.LibraryInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public abstract class VersionedFrameworkSupportProvider extends FrameworkSupportProvider {

  protected VersionedFrameworkSupportProvider(final @NonNls @NotNull String id, final @NotNull String title) {
    super(id, title);
  }

  protected abstract void addSupport(Module module, ModifiableRootModel rootModel, String version, final @Nullable Library library);

  @NotNull
  public String[] getVersions() {
    return new String[0];
  }

  @Nullable
  public String getDefaultVersion() {
    return null;
  }

  @NotNull
  public VersionConfigurable createConfigurable() {
    return new VersionConfigurable(getVersions(), getDefaultVersion());
  }

  @NotNull
  protected LibraryInfo[] getLibraries(final String selectedVersion) {
    return LibraryInfo.EMPTY_ARRAY;
  }

  @NotNull @NonNls
  protected String getLibraryName(final String selectedVersion) {
    return FrameworkSupportConfigurable.DEFAULT_LIBRARY_NAME;
  }

  public class VersionConfigurable extends FrameworkSupportConfigurable {
    private JComboBox myComboBox;

    public VersionConfigurable(String[] versions, String defaultVersion) {
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
      return VersionedFrameworkSupportProvider.this.getLibraries(getSelectedVersion());
    }

    @NonNls
    @NotNull
    public String getLibraryName() {
      return VersionedFrameworkSupportProvider.this.getLibraryName(getSelectedVersion());
    }

    public void addSupport(final Module module, final ModifiableRootModel rootModel, final @Nullable Library library) {
      VersionedFrameworkSupportProvider.this.addSupport(module, rootModel, getSelectedVersion(), library);
    }

    private String getSelectedVersion() {
      String version = null;
      if (myComboBox != null) {
        version = myComboBox.getSelectedItem().toString();
      }
      return version;
    }

  }
}
