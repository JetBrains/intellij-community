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

import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.PlatformIcons;
import org.jdesktop.swingx.combobox.ListComboBoxModel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class BrowserSelector {
  private final ComboboxWithBrowseButton myBrowserComboWithBrowse;

  public BrowserSelector(final boolean allowDefaultBrowser) {
    myBrowserComboWithBrowse = new ComboboxWithBrowseButton(new ComboBox(createBrowsersComboModel(allowDefaultBrowser)));
    myBrowserComboWithBrowse.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        WebBrowserManager browserManager = WebBrowserManager.getInstance();
        long modificationCount = browserManager.getModificationCount();
        ShowSettingsUtil.getInstance().editConfigurable(myBrowserComboWithBrowse, new BrowserSettings());

        WebBrowser selectedItem = getSelected();
        if (modificationCount != browserManager.getModificationCount()) {
          //noinspection unchecked
          myBrowserComboWithBrowse.getComboBox().setModel(createBrowsersComboModel(allowDefaultBrowser));
        }
        if (selectedItem != null) {
          setSelected(selectedItem);
        }
      }
    });

    //noinspection unchecked
    myBrowserComboWithBrowse.getComboBox().setRenderer(new ListCellRendererWrapper<WebBrowser>() {
      @Override
      public void customize(JList list,
                            WebBrowser value,
                            int index,
                            boolean selected,
                            boolean hasFocus) {
        Icon baseIcon = value != null ? value.getIcon() : PlatformIcons.WEB_ICON;
        setIcon(myBrowserComboWithBrowse.isEnabled() ? baseIcon : IconLoader.getDisabledIcon(baseIcon));
        setText(value != null ? value.getName() : "Default");
      }
    });
  }

  public JComponent getMainComponent() {
    return myBrowserComboWithBrowse;
  }

  @SuppressWarnings("Since15")
  private static ListComboBoxModel<WebBrowser> createBrowsersComboModel(boolean allowDefaultBrowser) {
    List<WebBrowser> activeBrowsers = new ArrayList<WebBrowser>();
    if (allowDefaultBrowser) {
      activeBrowsers.add(null);
    }
    activeBrowsers.addAll(WebBrowserManager.getInstance().getActiveBrowsers());
    return new ListComboBoxModel<WebBrowser>(activeBrowsers);
  }

  @SuppressWarnings({"deprecation", "UnusedDeclaration"})
  @Nullable
  @Deprecated
  /**
   * @deprecated  to remove in IDEA 14
   */
  public BrowserFamily getSelectedBrowser() {
    WebBrowser selected = getSelected();
    return selected == null ? null : selected.getFamily();
  }

  @Nullable
  public WebBrowser getSelected() {
    return (WebBrowser)myBrowserComboWithBrowse.getComboBox().getSelectedItem();
  }

  @Nullable
  public String getSelectedBrowserId() {
    WebBrowser browser = getSelected();
    return browser != null ? browser.getId().toString() : null;
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  /**
   * @deprecated  to remove in IDEA 14
   */
  public void setSelectedBrowser(@SuppressWarnings("deprecation") @Nullable BrowserFamily selectedItem) {
    setSelected(selectedItem == null ? null : WebBrowserManager.getInstance().findBrowser(selectedItem));
  }

  public void setSelected(@Nullable WebBrowser selectedItem) {
    myBrowserComboWithBrowse.getComboBox().setSelectedItem(selectedItem);
  }
}
