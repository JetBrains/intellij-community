/*
 * Copyright 2007 Sascha Weinreuter
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
package org.intellij.plugins.xpathView.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public abstract class ModeSwitchableDialog extends DialogWrapper {
  protected final DimensionService dimensionService = DimensionService.getInstance();
  private Mode myMode;

  public ModeSwitchableDialog(com.intellij.openapi.project.Project project, boolean canBeParent) {
    super(project, canBeParent);

    myMode = Mode.values()[StringUtilRt.parseInt(PropertiesComponent.getInstance().getValue(getPrivateDimensionServiceKey() + ".MODE", "0"), 0)];
  }

  @Override
  protected void init() {
    final Mode mode = myMode;
    myMode = null;
    setMode(mode);

    super.init();
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), new AbstractAction(myMode.other().getName()) {
      @Override
      public void actionPerformed(ActionEvent e) {
        putValue(Action.NAME, myMode.getName());

        dimensionService.setSize(getPrivateDimensionKey(), getSize());

        setMode(myMode.other());
        final Dimension size = dimensionService.getSize(getPrivateDimensionKey());
        if (size != null) {
          setSize(size.width, size.height);
          validate();
        }
        else {
          pack();
        }
      }
    }};
  }

  public final Mode getMode() {
    return myMode;
  }

  protected final void setMode(Mode mode) {
    setModeImpl(mode);
    this.myMode = mode;
  }

  @Override
  public void show() {
    final Window window = SwingUtilities.windowForComponent(getContentPane());
    window.addWindowListener(new WindowAdapter() {
      @Override
      public void windowOpened(WindowEvent e) {
        final Dimension size = dimensionService.getSize(getPrivateDimensionKey());
        if (size != null) {
          setSize(size.width, size.height);
          validate();
        }
      }
    });

    super.show();

    dimensionService.setSize(getPrivateDimensionKey(), getSize());
    dimensionService.setLocation(getPrivateDimensionKey(), getLocation());
    PropertiesComponent.getInstance().setValue(getPrivateDimensionServiceKey() + ".MODE", myMode.ordinal(), 0);
  }

  protected abstract void setModeImpl(Mode mode);

  @Override
  public Point getInitialLocation() {
    return dimensionService.getLocation(getPrivateDimensionKey());
  }

  @NotNull
  protected abstract String getPrivateDimensionServiceKey();

  protected final String getPrivateDimensionKey() {
    return getPrivateDimensionServiceKey() + "." + myMode.toString();
  }

  /**
   * @deprecated we gotta do this ourselves because this value is cached but the key for this dialog changes with mode changes
   */
  @Override
  protected final String getDimensionServiceKey() {
    //noinspection ConstantConditions
    return null;
  }

  @Override
  public boolean isOK() {
    return getExitCode() == OK_EXIT_CODE;
  }
}
