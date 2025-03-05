// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.refactorings.introduce;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.StringLenComparator;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.MavenDomProperties;
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates;
import org.jetbrains.idea.maven.utils.ComboBoxUtil;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

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
                                 @NotNull String selectedString) {
    super(project, true);
    myProject = project;
    myContext = context;
    myMavenDomProjectModel = mavenDomProjectModel;

    mySelectedString = selectedString;

    setTitle(MavenDomBundle.message("refactoring.introduce.property"));
    init();
  }

  @Override
  protected void dispose() {
    myNameField.removeDataChangedListener(myNameChangedListener);

    super.dispose();
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @Override
  protected void init() {
    super.init();
    updateOkStatus();
  }

  public String getEnteredName() {
    return myNameField.getEnteredName().trim();
  }

  public @NotNull MavenDomProjectModel getSelectedProject() {
    MavenDomProjectModel selectedItem =
      (MavenDomProjectModel)ComboBoxUtil.getSelectedValue((DefaultComboBoxModel)myMavenProjectsComboBox.getModel());

    return selectedItem == null ? myMavenDomProjectModel : selectedItem;
  }

  private String[] getSuggestions() {
    return getSuggestions(1);
  }

  private String[] getSuggestions(int level) {
    Collection<String> result = new HashSet<>();

    String value = mySelectedString.trim();
    boolean addUnqualifiedForm = true;

    XmlTag parent = PsiTreeUtil.getParentOfType(myContext, XmlTag.class, false);

    DomElement domParent = DomUtil.getDomElement(parent);
    if (domParent != null) {
      DomElement domSuperParent = domParent.getParent();
      DomFileElement<DomElement> domFile = DomUtil.getFileElement(domParent);
      if (domSuperParent != null && domFile != null && domFile.getRootElement() == domSuperParent) {
        value = domSuperParent.getXmlElementName();
        addUnqualifiedForm = false;
      }
      else {
        MavenDomShortArtifactCoordinates coordinates = DomUtil.getParentOfType(domParent, MavenDomShortArtifactCoordinates.class, false);
        if (coordinates != null && !(coordinates instanceof MavenDomProjectModel) && domParent != coordinates.getArtifactId()) {
          String artifactId = coordinates.getArtifactId().getStringValue();
          if (!StringUtil.isEmptyOrSpaces(artifactId)) {
            value = artifactId;
            addUnqualifiedForm = false;
          }
        }
      }
    }

    while (true) {
      String newValue = value.replaceAll("  ", " ");
      if (newValue.equals(value)) break;
      value = newValue;
    }

    value = value.replaceAll(" ", ".");
    List<String> parts = StringUtil.split(value, ".");
    String shortValue = parts.get(parts.size() - 1);

    if (addUnqualifiedForm) {
      result.add(value);
      result.add(shortValue);
    }

    String suffix = "";
    while (parent != null && level != 0) {
      suffix = parent.getName() + suffix;
      result.add(suffix);
      result.add(value + "." + suffix);
      result.add(shortValue + "." + suffix);
      suffix = "." + suffix;
      parent = parent.getParentTag();
      level--;
    }

    result = new ArrayList<>(result);
    ((List)result).sort(CodeStyleSettingsManager.getSettings(myProject).getCustomSettings(JavaCodeStyleSettings.class).PREFER_LONGER_NAMES ?
                        StringLenComparator.getDescendingInstance() : StringLenComparator.getInstance());
    return ArrayUtilRt.toStringArray(result);
  }

  private static String joinWords(@NotNull String s, @NotNull String delimiter) {
    return joinWords(StringUtil.split(s, delimiter));
  }

  private static String joinWords(@NotNull List<String> stringList) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < stringList.size(); i++) {
      String word = stringList.get(i);
      if (!StringUtil.isEmptyOrSpaces(word)) {
        sb.append(i == 0 ? StringUtil.decapitalize(word.trim()) : StringUtil.capitalize(word.trim()));
      }
    }
    return sb.toString();
  }

  @Override
  protected JComponent createCenterPanel() {
    myFieldNamePanel.setLayout(new BorderLayout());

    myNameField = new NameSuggestionsField(myProject);
    myNameChangedListener = () -> updateOkStatus();
    myNameField.addDataChangedListener(myNameChangedListener);
    myNameField.setSuggestions(getSuggestions());

    myFieldNamePanel.add(myNameField, BorderLayout.CENTER);

    List<MavenDomProjectModel> projects = getProjects();

    ComboBoxUtil.setModel(myMavenProjectsComboBox, new DefaultComboBoxModel(), projects,
                          model -> Pair.create(MavenDomUtil.getProjectName(model), model));

    myMavenProjectsComboBox.setSelectedItem(myMavenDomProjectModel);

    return myMainPanel;
  }


  private List<MavenDomProjectModel> getProjects() {
    List<MavenDomProjectModel> projects = new ArrayList<>();

    projects.add(myMavenDomProjectModel);
    projects.addAll(MavenDomProjectProcessorUtils.collectParentProjects(myMavenDomProjectModel));

    return projects;
  }

  private void updateOkStatus() {
    String text = getEnteredName();

    setOKActionEnabled(!StringUtil.isEmptyOrSpaces(text) && !isContainWrongSymbols(text) && !isPropertyExist(text));
  }

  private static boolean isContainWrongSymbols(@NotNull String text) {
    return text.isEmpty() || Character.isDigit(text.charAt(0)) || StringUtil.containsAnyChar(text, "\t ;*'\"\\/,()^&<>={}[]");
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

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField.getFocusableComponent();
  }
}