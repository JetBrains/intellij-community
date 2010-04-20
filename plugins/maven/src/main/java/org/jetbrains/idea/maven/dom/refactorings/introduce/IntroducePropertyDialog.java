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
package org.jetbrains.idea.maven.dom.refactorings.introduce;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.util.Function;
import com.intellij.util.containers.OrderedSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.MavenDomProperties;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.ComboBoxUtil;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class IntroducePropertyDialog extends DialogWrapper {

  private final Project myProject;
  private final XmlElement myContext;
  private final MavenDomProjectModel myMavenDomProjectModel;

  private final String mySelectedString;
  private NameSuggestionsField myNameField;
  private NameSuggestionsField.DataChanged myNameChangedListener;

  private JComboBox myMavenProjectsComboBox;
  private JPanel myMainPanel;
  private JPanel myFieldNamePanel;

  public IntroducePropertyDialog(@NotNull Project project,
                                 @NotNull XmlElement context,
                                 @NotNull MavenDomProjectModel mavenDomProjectModel,
                                 IntroduceVariableHandler.Validator validator,
                                 @NotNull String selectedString) {
    super(project, true);
    myProject = project;
    myContext = context;
    myMavenDomProjectModel = mavenDomProjectModel;

    mySelectedString = selectedString;

    setTitle(MavenDomBundle.message("refactoring.introduce.property"));
    init();
  }

  protected void dispose() {
    myNameField.removeDataChangedListener(myNameChangedListener);

    super.dispose();
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  protected void init() {
    super.init();
    updateOkStatus();
  }

  public String getEnteredName() {
    return myNameField.getEnteredName();
  }

  @NotNull
  public MavenDomProjectModel getSelectedProject() {
    MavenDomProjectModel selectedItem =
      (MavenDomProjectModel)ComboBoxUtil.getSelectedValue((DefaultComboBoxModel)myMavenProjectsComboBox.getModel());

    return selectedItem == null ? myMavenDomProjectModel : selectedItem;
  }

  private String[] getSuggestions() {
    return getSuggestions(2);
  }

  private String[] getSuggestions(int level) {
    Set<String> suggestions = new OrderedSet<String>();

    if (mySelectedString.contains(".")) {
      suggestions.add(mySelectedString.replaceAll(" ", ""));
      suggestions.add(joinWords(mySelectedString, "."));
    }
    else {
      suggestions.add(joinWords(StringUtil.split(mySelectedString, " ")));
    }

    XmlTag parent = PsiTreeUtil.getParentOfType(myContext, XmlTag.class, false);
    String sb = "";
    while (parent != null && level != 0) {
      sb = parent.getName() + sb;
      suggestions.add(sb);
      suggestions.add(joinWords(sb, "."));
      sb = "." + sb;

      parent = parent.getParentTag();
      level--;
    }

    return suggestions.toArray(new String[suggestions.size()]);
  }

  private static String joinWords(@NotNull String s, @NotNull String delimiter) {
    return joinWords(StringUtil.split(s, delimiter));
  }

  private static String joinWords(@NotNull List<String> stringList) {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < stringList.size(); i++) {
      String word = stringList.get(i);
      if (!StringUtil.isEmptyOrSpaces(word)) {
        sb.append(i == 0 ? StringUtil.decapitalize(word.trim()) : StringUtil.capitalize(word.trim()));
      }
    }
    return sb.toString();
  }

  protected JComponent createCenterPanel() {
    myFieldNamePanel.setLayout(new BorderLayout());

    myNameField = new NameSuggestionsField(myProject);
    myNameChangedListener = new NameSuggestionsField.DataChanged() {
      public void dataChanged() {
        updateOkStatus();
      }
    };
    myNameField.addDataChangedListener(myNameChangedListener);
    myNameField.setSuggestions(getSuggestions());

    myFieldNamePanel.add(myNameField, BorderLayout.CENTER);

    List<MavenDomProjectModel> projects = getProjects();

    ComboBoxUtil
      .setModel(myMavenProjectsComboBox, new DefaultComboBoxModel(), projects, new Function<MavenDomProjectModel, Pair<String, ?>>() {
        public Pair<String, ?> fun(MavenDomProjectModel model) {
          String projectName = model.getName().getStringValue();
          MavenProject mavenProject = MavenDomUtil.findProject(model);
          if (mavenProject != null) {
            projectName = mavenProject.getDisplayName();
          }
          if (StringUtil.isEmptyOrSpaces(projectName)) {
            projectName = "pom.xml";
          }
          return Pair.create(projectName, model);
        }
      });

    myMavenProjectsComboBox.setSelectedItem(myMavenDomProjectModel);

    return myMainPanel;
  }


  private List<MavenDomProjectModel> getProjects() {
    List<MavenDomProjectModel> projects = new ArrayList<MavenDomProjectModel>();

    projects.add(myMavenDomProjectModel);
    projects.addAll(MavenDomProjectProcessorUtils.collectParentProjects(myMavenDomProjectModel));

    return projects;
  }

  private void updateOkStatus() {
    String text = getEnteredName();

    setOKActionEnabled(!StringUtil.isEmptyOrSpaces(text) && !isContainWrongSymbols(text) && !isPropertyExist(text));
  }

  private static boolean isContainWrongSymbols(@NotNull String text) {
    return text.length() == 0 || StringUtil.containsAnyChar(text, "\t ;*'\"\\/,()^&<>={}[]") ;
  }

  private boolean isPropertyExist(@NotNull String text) {
    MavenDomProjectModel project = getSelectedProject();

    if (isPropertyExist(text, project)) return true;

    for (MavenDomProjectModel child : MavenDomProjectProcessorUtils.getChildrenProjects(project)) {
      if (isPropertyExist(text, child)) return true;
    }

    for (MavenDomProjectModel parent : MavenDomProjectProcessorUtils.collectParentProjects(project)) {
      if (isPropertyExist(text, parent)) return true;
    }
    return false;  
  }

  private static boolean isPropertyExist(String propertyName, MavenDomProjectModel project) {
    MavenDomProperties props = project.getProperties();

    XmlTag propsTag = props.getXmlTag();
    if (propsTag != null) {
      for (XmlTag each : propsTag.getSubTags()) {
         if (propertyName.equals(each.getName())) return true;
      }
    }
    return false;
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField.getFocusableComponent();
  }
}