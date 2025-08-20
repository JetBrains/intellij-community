/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.config.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import java.util.Objects;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.intellij.plugins.intelliLang.inject.config.XmlTagInjection;

public class XmlTagPanel extends AbstractInjectionPanel<XmlTagInjection> {

  // read by reflection
  LanguagePanel myLanguagePanel;
  TagPanel myPanel;
  AdvancedXmlPanel myAdvancedPanel;

  private JPanel myRoot;
  private JTextField myNameTextField;

  private boolean myUseGeneratedName;

  public XmlTagPanel(XmlTagInjection injection, Project project) {
    super(injection, project);
    $$$setupUI$$$(); // see IDEA-9987

    init(injection.copy());
  }

  @Override
  protected void apply(XmlTagInjection other) {
    String name = myNameTextField.getText();
    boolean useGenerated = myUseGeneratedName && Objects.equals(myOrigInjection.getDisplayName(), name);
    String newName = useGenerated || StringUtil.isEmptyOrSpaces(name) ? other.getGeneratedName() : name;
    other.setDisplayName(newName);
  }

  @Override
  protected void resetImpl() {
    myNameTextField.setText(myOrigInjection.getDisplayName());

    myUseGeneratedName = Objects.equals(myOrigInjection.getDisplayName(), myOrigInjection.getGeneratedName());
  }

  @Override
  public JPanel getComponent() {
    return myRoot;
  }

  private void createUIComponents() {
    myLanguagePanel = new LanguagePanel(myProject, myOrigInjection);
    myPanel = new TagPanel(myProject, myOrigInjection);
    myAdvancedPanel = new AdvancedXmlPanel(myProject, myOrigInjection);
  }

  private void $$$setupUI$$$() {
  }
}
