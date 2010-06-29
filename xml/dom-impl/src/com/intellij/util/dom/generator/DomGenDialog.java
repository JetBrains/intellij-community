/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.util.dom.generator;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.io.File;

/**
 * @author Konstantin Bulenkov
 */
public class DomGenDialog extends DialogWrapper{
  final DomGenPanel panel;
  final JComponent comp;

  protected DomGenDialog(Project project) {
    super(project);
    panel = new DomGenPanel(project);
    comp = panel.getComponent();
    init();
    getOKAction().putValue(Action.NAME, "Generate");
  }

  @Override
  protected JComponent createCenterPanel() {
    return comp;
  }


  @Override
  protected void doOKAction() {
    final String location = panel.getLocation();
    ModelLoader loader = location.toLowerCase().endsWith(".xsd") ? new XSDModelLoader() : new DTDModelLoader();
    final ModelGen modelGen = new ModelGen(loader);
    final NamespaceDesc desc = panel.getNamespaceDescriptor();
    modelGen.setConfig(desc.name, location, desc);
    try {
      final File output = new File(panel.getOutputDir());
      modelGen.perform(output, new File(location).getParentFile());
    } catch (Exception e) {
      e.printStackTrace(System.err);
    }
    super.doOKAction();
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }
}
