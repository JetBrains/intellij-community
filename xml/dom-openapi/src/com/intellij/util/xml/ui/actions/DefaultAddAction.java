// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.NlsActions;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.Type;

public abstract class DefaultAddAction<T extends DomElement> extends AnAction {

  public DefaultAddAction() {
    super(XmlDomBundle.messagePointer("dom.action.add"));
  }

  public DefaultAddAction(@NlsActions.ActionText String text) {
    super(text);
  }

  public DefaultAddAction(@NlsActions.ActionText String text,
                          @NlsActions.ActionDescription String description, Icon icon) {
    super(text, description, icon);
  }


  protected Type getElementType() {
    return getDomCollectionChildDescription().getType();
  }

  protected void tuneNewValue(T t) {
  }

  protected abstract DomCollectionChildDescription getDomCollectionChildDescription();

  protected abstract DomElement getParentDomElement();

  protected void afterAddition(@NotNull T newElement) {
  }

  @Override
  public final void actionPerformed(@NotNull final AnActionEvent e) {
    final T result = performElementAddition();
    if (result != null) {
      afterAddition(result);
    }
  }

  @Nullable
  protected T performElementAddition() {
    final DomElement parent = getParentDomElement();
    final DomManager domManager = parent.getManager();
    final TypeChooser[] oldChoosers = new TypeChooser[]{null};
    final Type[] aClass = new Type[]{null};
    final StableElement<T> result = WriteCommandAction.writeCommandAction(domManager.getProject(), DomUtil.getFile(parent)).compute(() -> {
      final DomElement parentDomElement = getParentDomElement();
      final T t = (T)getDomCollectionChildDescription().addValue(parentDomElement, getElementType());
      tuneNewValue(t);
      aClass[0] = parent.getGenericInfo().getCollectionChildDescription(t.getXmlElementName()).getType();
      oldChoosers[0] = domManager.getTypeChooserManager().getTypeChooser(aClass[0]);
      final SmartPsiElementPointer pointer =
        SmartPointerManager.getInstance(domManager.getProject()).createSmartPsiElementPointer(t.getXmlTag());
      domManager.getTypeChooserManager().registerTypeChooser(aClass[0], new TypeChooser() {
        @Override
        public Type chooseType(final XmlTag tag) {
          if (tag == pointer.getElement()) {
            return getElementType();
          }
          return oldChoosers[0].chooseType(tag);
        }

        @Override
        public void distinguishTag(final XmlTag tag, final Type aClass) throws IncorrectOperationException {
          oldChoosers[0].distinguishTag(tag, aClass);
        }

        @Override
        public Type[] getChooserTypes() {
          return oldChoosers[0].getChooserTypes();
        }
      });
      return t.createStableCopy();
    });
    if (result != null) {
      domManager.getTypeChooserManager().registerTypeChooser(aClass[0], oldChoosers[0]);
      return result.getWrappedElement();
    }
    return null;
  }
}