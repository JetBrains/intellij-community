// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger;


import com.intellij.openapi.options.ConfigurableUi;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class KotlinDelegatedPropertyRendererConfigurableUi implements ConfigurableUi<KotlinDebuggerSettings> {
    private JCheckBox disableCoroutineAgent;
    private JPanel myPanel;

    @Override
    public void reset(@NotNull KotlinDebuggerSettings settings) {
        disableCoroutineAgent.setSelected(settings.getDebugDisableCoroutineAgent());
    }

    @Override
    public boolean isModified(@NotNull KotlinDebuggerSettings settings) {
        return settings.getDebugDisableCoroutineAgent() != disableCoroutineAgent.isSelected();
    }

    @Override
    public void apply(@NotNull KotlinDebuggerSettings settings) {
        settings.setDebugDisableCoroutineAgent(disableCoroutineAgent.isSelected());
    }

    @NotNull
    @Override
    public JComponent getComponent() {
        return myPanel;
    }
}
