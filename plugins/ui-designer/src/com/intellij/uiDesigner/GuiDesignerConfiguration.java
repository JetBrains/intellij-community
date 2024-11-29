// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiModifier;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
@State(name = "uidesigner-configuration", storages = @Storage("uiDesigner.xml"))
public final class GuiDesignerConfiguration implements PersistentStateComponent<GuiDesignerConfiguration> {
  public static GuiDesignerConfiguration getInstance(final Project project){
    return project.getService(GuiDesignerConfiguration.class);
  }

  /**
   * Defines how the designer generate UI (instrument classes or generate Java code)
   */
  public boolean INSTRUMENT_CLASSES = true;

  public boolean COPY_FORMS_RUNTIME_TO_OUTPUT = true;

  /**
   * If INSTRUMENT_CLASSES is false, the user may select between generating source files
   * upon Build (default, GENERATE_SOURCES_ON_SAVE is false) and
   * generating right after saving the form (GENERATE_SOURCES_ON_SAVE is true)
   */
  public boolean GENERATE_SOURCES_ON_SAVE = false;

  public @NlsSafe String DEFAULT_LAYOUT_MANAGER = UIFormXmlConstants.LAYOUT_INTELLIJ;

  public @NlsSafe String DEFAULT_FIELD_ACCESSIBILITY = PsiModifier.PRIVATE;

  public boolean RESIZE_HEADERS = true;

  public boolean USE_DYNAMIC_BUNDLES = false;

  @Override
  public GuiDesignerConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull GuiDesignerConfiguration object) {
    XmlSerializerUtil.copyBean(object, this);
  }
}
