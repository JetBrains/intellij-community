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

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.DimensionService;
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

        final int state = dimensionService.getExtendedState(getPrivateDimensionServiceKey() + ".MODE");
        myMode = (state == -1 ? Mode.values()[0] : Mode.values()[state]);
    }

    protected void init() {
        final Mode mode = myMode;
        myMode = null;
        setMode(mode);

        super.init();
    }

    @NotNull
    protected Action[] createActions() {
        return new Action[]{ getOKAction(), getCancelAction(), new AbstractAction(myMode.other().getName()) {
            public void actionPerformed(ActionEvent e) {
                putValue(Action.NAME, myMode.getName());

                dimensionService.setSize(getPrivateDimensionKey(), getSize());

                setMode(myMode.other());
                final Dimension size = dimensionService.getSize(getPrivateDimensionKey());
                if (size != null) {
                    setSize(size.width, size.height);
                    validate();
                } else {
                    pack();
                }
            }
        } };
    }

    public final Mode getMode() {
        return myMode;
    }

    protected final void setMode(Mode mode) {
        setModeImpl(mode);
        this.myMode = mode;
    }

    public void show() {
        final Window window = SwingUtilities.windowForComponent(getContentPane());
        window.addWindowListener(new WindowAdapter() {
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
        dimensionService.setExtendedState(getPrivateDimensionServiceKey() + ".MODE", myMode.ordinal());
    }

    protected abstract void setModeImpl(Mode mode);

    public Point getInitialLocation() {
        return dimensionService.getLocation(getPrivateDimensionKey());
    }

    protected abstract String getPrivateDimensionServiceKey();

    protected final String getPrivateDimensionKey() {
        return getPrivateDimensionServiceKey() + "." + myMode.toString();
    }

    /** @deprecated we gotta do this ourselves because this value is cached but the key for this dialog changes with mode changes */
    protected final String getDimensionServiceKey() {
        //noinspection ConstantConditions
        return null;
    }

    public boolean isOK() {
        return getExitCode() == OK_EXIT_CODE;
    }
}
