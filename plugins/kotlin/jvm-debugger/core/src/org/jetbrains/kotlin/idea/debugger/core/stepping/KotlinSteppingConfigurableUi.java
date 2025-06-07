// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.stepping;


import com.intellij.openapi.options.ConfigurableUi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.debugger.KotlinDebuggerSettings;

import javax.swing.*;

public class KotlinSteppingConfigurableUi implements ConfigurableUi<KotlinDebuggerSettings> {
    private JCheckBox ignoreKotlinMethods;
    private JCheckBox alwaysDoSmartStepInto;
    private JPanel myPanel;

    @Override
    public void reset(@NotNull KotlinDebuggerSettings settings) {
        ignoreKotlinMethods.setSelected(settings.getDisableKotlinInternalClasses());
        alwaysDoSmartStepInto.setSelected(settings.getAlwaysDoSmartStepInto());
    }

    @Override
    public boolean isModified(@NotNull KotlinDebuggerSettings settings) {
        return settings.getDisableKotlinInternalClasses() != ignoreKotlinMethods.isSelected() ||
               settings.getAlwaysDoSmartStepInto() != alwaysDoSmartStepInto.isSelected();
    }

    @Override
    public void apply(@NotNull KotlinDebuggerSettings settings) {
        settings.setDisableKotlinInternalClasses(ignoreKotlinMethods.isSelected());
        settings.setAlwaysDoSmartStepInto(alwaysDoSmartStepInto.isSelected());
    }

    @Override
    public @NotNull JComponent getComponent() {
        return myPanel;
    }
}
