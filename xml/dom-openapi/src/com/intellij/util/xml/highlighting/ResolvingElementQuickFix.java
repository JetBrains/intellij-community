/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.ide.TypePresentationService;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @author Dmitry Avdeev
*/
public class ResolvingElementQuickFix implements LocalQuickFix, IntentionAction {

  private final String myNewName;
  private final List<? extends DomElement> myParents;
  private final DomCollectionChildDescription myChildDescription;
  private String myTypeName;

  public ResolvingElementQuickFix(final Class<? extends DomElement> clazz, final String newName, final List<? extends DomElement> parents,
                                  final DomCollectionChildDescription childDescription) {
    myNewName = newName;
    myParents = parents;
    myChildDescription = childDescription;

    myTypeName = TypePresentationService.getService().getTypePresentableName(clazz);
  }

  public void setTypeName(final String typeName) {
    myTypeName = typeName;
  }

  @Override
  @NotNull
  public String getName() {
    return XmlDomBundle.message("dom.quickfix.create.new.element.name", myTypeName, myNewName);
  }

  @Override
  @NotNull
  public String getText() {
    return getName();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return XmlDomBundle.message("dom.quickfix.create.new.element.family");
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    applyFix();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    applyFix();
  }

  private void applyFix() {
    chooseParent(myParents,
                 parent -> WriteCommandAction.writeCommandAction(parent.getManager().getProject(), DomUtil.getFile(parent)).run(() -> doFix(parent, myChildDescription, myNewName)));
  }

  protected DomElement doFix(DomElement parent, final DomCollectionChildDescription childDescription, String newName) {
    final DomElement domElement = childDescription.addValue(parent);
    final GenericDomValue nameDomElement = domElement.getGenericInfo().getNameDomElement(domElement);
    assert nameDomElement != null;
    nameDomElement.setStringValue(newName);
    return domElement;
  }

  protected static void chooseParent(final List<? extends DomElement> files, final Consumer<? super DomElement> onChoose) {
    switch (files.size()) {
      case 0 -> {}
      case 1 -> onChoose.consume(files.iterator().next());
      default -> JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<DomElement>(XmlDomBundle.message(
        "dom.quickfix.create.new.element.choose.file.title"), files) {
        @Override
        public PopupStep onChosen(final DomElement selectedValue, final boolean finalChoice) {
          onChoose.consume(selectedValue);
          return super.onChosen(selectedValue, finalChoice);
        }

        @Override
        public Icon getIconFor(final DomElement aValue) {
          return DomUtil.getFile(aValue).getIcon(0);
        }

        @Override
        @NotNull
        public String getTextFor(final DomElement value) {
          return DomUtil.getFile(value).getName();
        }
      }).showInBestPositionFor(DataManager.getInstance().getDataContext());
    }
  }

  @Nullable
  public static <T extends DomElement> DomCollectionChildDescription getChildDescription(final List<? extends DomElement> contexts, Class<T> clazz) {

    if (contexts.size() == 0) {
        return null;
    }
    final DomElement context = contexts.get(0);
    final DomGenericInfo genericInfo = context.getGenericInfo();
    final List<? extends DomCollectionChildDescription> descriptions = genericInfo.getCollectionChildrenDescriptions();
    for (DomCollectionChildDescription description : descriptions) {
      final Type type = description.getType();
      if (type.equals(clazz)) {
        return description;
      }
    }
    return null;
  }

  @Nullable
  public static ResolvingElementQuickFix createFix(final String newName, final Class<? extends DomElement> clazz, final DomElement scope) {
    final List<DomElement> parents = ModelMergerUtil.getImplementations(scope);
    return createFix(newName, clazz, parents);
  }

  @Nullable
  public static ResolvingElementQuickFix createFix(final String newName, final Class<? extends DomElement> clazz, final List<? extends DomElement> parents) {
    final DomCollectionChildDescription childDescription = getChildDescription(parents, clazz);
    if (newName.length() > 0 && childDescription != null) {
      return new ResolvingElementQuickFix(clazz, newName, parents, childDescription);
    }
    return null;
  }

  public static LocalQuickFix[] createFixes(final String newName, Class<? extends DomElement> clazz, final DomElement scope) {
    final LocalQuickFix fix = createFix(newName, clazz, scope);
    return fix != null ? new LocalQuickFix[] { fix } : LocalQuickFix.EMPTY_ARRAY;
  }
}
