// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner;

import com.intellij.application.options.editor.EditorOptionsTopHitProviderBase;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;

/**
 * @author Sergey.Malenkov
 */
final class GuiDesignerOptionsTopHitProvider extends EditorOptionsTopHitProviderBase {
  @Override
  protected Configurable getConfigurable(Project project) {
    return new GuiDesignerConfigurable(project);
  }
}
