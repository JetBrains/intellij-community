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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import java.util.Objects;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.intellij.lang.regexp.RegExpLanguage;
import org.intellij.plugins.intelliLang.inject.config.XmlAttributeInjection;

public class XmlAttributePanel extends AbstractInjectionPanel<XmlAttributeInjection> {

  private JPanel myRoot;

  // read by reflection
  LanguagePanel myLanguagePanel;
  TagPanel myTagPanel;
  AdvancedXmlPanel myAdvancedPanel;

  private EditorTextField myLocalName;
  private ComboBox myNamespace;
  private JTextField myNameTextField;

  private boolean myUseGeneratedName;

  public XmlAttributePanel(XmlAttributeInjection injection, Project project) {
    super(injection, project);
    $$$setupUI$$$(); // see IDEA-9987

    myNamespace.setModel(TagPanel.createNamespaceUriModel(project));

    init(injection.copy());
  }

  @Override
  public JPanel getComponent() {
    return myRoot;
  }

  @Override
  protected void resetImpl() {
    myNameTextField.setText(myOrigInjection.getDisplayName());
    myLocalName.setText(myOrigInjection.getAttributeName());
    myNamespace.getEditor().setItem(myOrigInjection.getAttributeNamespace());

    myUseGeneratedName = Objects.equals(myOrigInjection.getDisplayName(), myOrigInjection.getGeneratedName());
  }

  @Override
  protected void apply(XmlAttributeInjection other) {
    other.setAttributeName(myLocalName.getText());
    other.setAttributeNamespace(getNamespace());

    String name = myNameTextField.getText();
    boolean useGenerated = myUseGeneratedName && Objects.equals(myOrigInjection.getDisplayName(), name);
    String newName = useGenerated || StringUtil.isEmptyOrSpaces(name) ? other.getGeneratedName() : name;
    other.setDisplayName(newName);
  }

  private String getNamespace() {
    final String s = (String)myNamespace.getEditor().getItem();
    return s != null ? s : "";
  }

  private void createUIComponents() {
    myLanguagePanel = new LanguagePanel(myProject, myOrigInjection);
    myTagPanel = new TagPanel(myProject, myOrigInjection);
    myAdvancedPanel = new AdvancedXmlPanel(myProject, myOrigInjection);

    myLocalName = new LanguageTextField(RegExpLanguage.INSTANCE, myProject, myOrigInjection.getAttributeName());

    myNamespace = new ComboBox(200);
  }

  private void $$$setupUI$$$() {
  }
}
