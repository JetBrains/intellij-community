// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.stepping;


import com.intellij.openapi.options.ConfigurableUi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.debugger.KotlinDebuggerSettings;

import javax.swing.*;

public class KotlinSteppingConfigurableUi implements ConfigurableUi<KotlinDebuggerSettings> {
    private JCheckBox ignoreKotlinMethods;
    private JPanel myPanel;

    @Override
    public void reset(@NotNull KotlinDebuggerSettings settings) {
        boolean flag = settings.getDisableKotlinInternalClasses();
        ignoreKotlinMethods.setSelected(flag);
    }

    @Override
    public boolean isModified(@NotNull KotlinDebuggerSettings settings) {
        return settings.getDisableKotlinInternalClasses() != ignoreKotlinMethods.isSelected();
    }

    @Override
    public void apply(@NotNull KotlinDebuggerSettings settings) {
        settings.setDisableKotlinInternalClasses(ignoreKotlinMethods.isSelected());
    }

    @NotNull
    @Override
    public JComponent getComponent() {
        return myPanel;
    }
}
