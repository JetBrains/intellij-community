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

package com.intellij.util.xml.tree.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.TypePresentationService;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.tree.AbstractDomElementNode;
import com.intellij.util.xml.tree.BaseDomElementNode;
import com.intellij.util.xml.tree.DomModelTreeView;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * User: Sergey.Vasiliev
 */
public class DomElementsToggleAction extends ToggleAction {
  private final DomModelTreeView myTreeView;
  private final Class myClass;
  private final Icon myIcon;
  private final String myText;


  public DomElementsToggleAction(final DomModelTreeView treeView, final Class aClass) {
    myTreeView = treeView;
    myClass = aClass;

    Icon myIcon = ElementPresentationManager.getIcon(myClass);
    if (myIcon == null) {
      myIcon = AllIcons.Nodes.Pointcut;
    }
    this.myIcon = myIcon;

    myText = TypePresentationService.getService().getTypePresentableName(myClass);

    if(getHiders() == null) DomUtil.getFile(myTreeView.getRootElement()).putUserData(AbstractDomElementNode.TREE_NODES_HIDERS_KEY,
                                                                                     new HashMap<>());

    if(getHiders().get(myClass) == null) getHiders().put(myClass, true);
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);

    e.getPresentation().setIcon(myIcon);
    e.getPresentation().setText((getHiders().get(myClass) ? "Hide ":"Show ")+myText);

    e.getPresentation().setEnabled(getHiders() != null && getHiders().get(myClass)!=null);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return getHiders().get(myClass);
  }

  private Map<Class, Boolean> getHiders() {
    return DomUtil.getFile(myTreeView.getRootElement()).getUserData(BaseDomElementNode.TREE_NODES_HIDERS_KEY);
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    getHiders().put(myClass, state);
    myTreeView.getBuilder().updateFromRoot();
  }
}

