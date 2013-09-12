/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.browsers;

import com.intellij.ide.BrowserSettings;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class BrowserSelector {
  private ComboboxWithBrowseButton myBrowserComboWithBrowse;

  public BrowserSelector(final boolean allowDefaultBrowser) {
    myBrowserComboWithBrowse = new ComboboxWithBrowseButton(new ComboBox());
    myBrowserComboWithBrowse.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final ShowSettingsUtil util = ShowSettingsUtil.getInstance();
        util.editConfigurable(myBrowserComboWithBrowse, new BrowserSettings());

        final BrowsersConfiguration.BrowserFamily selectedItem = getSelectedBrowser();
        initBrowsersComboModel(allowDefaultBrowser);
        if (selectedItem != null) {
          setSelectedBrowser(selectedItem);
        }
      }
    });

    final JComboBox comboBox = myBrowserComboWithBrowse.getComboBox();
    comboBox.setRenderer(new ListCellRendererWrapper<BrowsersConfiguration.BrowserFamily>() {
      @Override
      public void customize(JList list,
                            BrowsersConfiguration.BrowserFamily value,
                            int index,
                            boolean selected,
                            boolean hasFocus) {
        final Icon baseIcon = value != null ? value.getIcon() : PlatformIcons.WEB_ICON;
        final Icon icon = myBrowserComboWithBrowse.isEnabled() ? baseIcon : IconLoader.getDisabledIcon(baseIcon);
        setIcon(icon);
        setText(value != null ? value.getName() : "Default");
      }
    });

    initBrowsersComboModel(allowDefaultBrowser);
  }

  public JComponent getMainComponent() {
    return myBrowserComboWithBrowse;
  }

  private void initBrowsersComboModel(boolean allowDefaultBrowser) {
    final List<BrowsersConfiguration.BrowserFamily> activeBrowsers = new ArrayList<BrowsersConfiguration.BrowserFamily>();
    if (allowDefaultBrowser) {
      activeBrowsers.add(null);
    }
    activeBrowsers.addAll(BrowsersConfiguration.getInstance().getActiveBrowsers());

    myBrowserComboWithBrowse.getComboBox().setModel(new DefaultComboBoxModel(ArrayUtil.toObjectArray(activeBrowsers)));
  }

  @Nullable
  public BrowsersConfiguration.BrowserFamily getSelectedBrowser() {
    return (BrowsersConfiguration.BrowserFamily)myBrowserComboWithBrowse.getComboBox().getSelectedItem();
  }

  @Nullable
  public String getSelectedBrowserFamilyName() {
    final BrowsersConfiguration.BrowserFamily browser = getSelectedBrowser();
    return browser != null ? browser.getName() : null;
  }

  public void setSelectedBrowser(@Nullable BrowsersConfiguration.BrowserFamily selectedItem) {
    myBrowserComboWithBrowse.getComboBox().setSelectedItem(selectedItem);
  }
}
