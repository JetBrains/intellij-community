/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.Icons;
import com.jetbrains.python.PyIcons;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Handles nodes in Structure View.
 * User: yole
 * Date: 05.06.2005
 */
public class PyStructureViewElement implements StructureViewTreeElement {

  private enum Visibility {
    NORMAL, // visible
    INVISIBLE, // not visible: e.g. local to function
    PRIVATE,  // "__foo" in a class
    PREDEFINED // like "__init__"; only if really visible
  }

  private PyElement my_element;
  private Visibility my_visibility;
  private Icon my_icon;

  public PyStructureViewElement(PyElement element, Visibility vis) {
    my_element = element;
    my_visibility = vis;
  }

  public PyStructureViewElement(PyElement element) {
    this(element, Visibility.NORMAL);
  }

  public PyElement getValue() {
    return my_element;
  }

  public void navigate(boolean requestFocus) {
    ((NavigationItem)my_element).navigate(requestFocus);
  }

  public boolean canNavigate() {
    return ((NavigationItem)my_element).canNavigate();
  }

  public boolean canNavigateToSource() {
    return ((NavigationItem)my_element).canNavigateToSource();
  }

  public void setIcon(Icon icon) {
    my_icon = icon;
  }

  public StructureViewTreeElement[] getChildren() {
    final Set<PyElement> childrenElements = new HashSet<PyElement>();
    my_element.acceptChildren(new PyElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (isWorthyClassItem(element)) {
          childrenElements.add((PyElement)element);
        }
        else {
          element.acceptChildren(this);
        }
      }

      public void visitPyParameter(final PyNamedParameter node) {
        // Do not add parameters to structure view
      }
    });

    StructureViewTreeElement[] children = new StructureViewTreeElement[childrenElements.size()];
    int i = 0;
    for (PyElement element : childrenElements) {
      // look at functions and predefined __names__
      Visibility vis = Visibility.NORMAL;
      if (PsiTreeUtil.getParentOfType(element, PyFunction.class) != null) {
        // whatever is defined inside a def, is hidden
        vis = Visibility.INVISIBLE;
      }
      else {
        String name = element.getName();
        if (name != null && name.startsWith("__")) {
          if (PyNames.UnderscoredNames.contains(name)) {
            vis = Visibility.PREDEFINED;
          }
          else {
            vis = Visibility.PRIVATE;
          }
        }
      }
      children[i] = new PyStructureViewElement(element, vis);
      if (element instanceof PyClass) {
        PyClass the_exception = PyBuiltinCache.getInstance(element.getProject()).getClass("Exception");
        final PyClass cls = (PyClass)element;
        for (PyClass anc : cls.iterateAncestors()) {
          if (anc == the_exception) {
            ((PyStructureViewElement)(children[i])).setIcon(Icons.EXCEPTION_CLASS_ICON);
            break;
          }
        }
      }
      i += 1;
    }

    return children;
  }

  static boolean isWorthyClassItem(PsiElement element) {
    if (element instanceof PyClass) return true;
    if (element instanceof PyFunction) return true;
    if ((element instanceof PyTargetExpression) && ((PyTargetExpression)element).getQualifier() == null) {
      PsiElement e = element.getParent();
      if (e instanceof PyAssignmentStatement) {
        e = e.getParent();
        if (e instanceof PyStatementList) {
          e = e.getParent();
          if (e instanceof PyClass) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        if (my_element instanceof PyFunction) {
          PsiElement[] children = my_element.getChildren();
          if (children.length > 0 && children[0] instanceof PyParameterList) {
            PyParameterList argList = (PyParameterList)children[0];
            StringBuilder result = new StringBuilder(my_element.getName());
            ParamHelper.appendParameterList(argList, result);
            return result.toString();
          }
        }
        return my_element.getName();
      }

      public
      @Nullable
      TextAttributesKey getTextAttributesKey() {
        return null;
      }

      public
      @Nullable
      String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        Icon normal_icon = my_element.getIcon(Iconable.ICON_FLAG_OPEN);
        if (my_icon != null) normal_icon = my_icon; // override normal
        if (my_visibility == Visibility.NORMAL) {
          return normal_icon;
        }
        else {
          LayeredIcon icon = new LayeredIcon(2);
          icon.setIcon(normal_icon, 0);
          Icon overlay = null;
          if (my_visibility == Visibility.PRIVATE) {
            overlay = PyIcons.PRIVATE;
          }
          else if (my_visibility == Visibility.PREDEFINED) {
            overlay = PyIcons.PREDEFINED;
          }
          else if (my_visibility == Visibility.INVISIBLE) overlay = PyIcons.INVISIBLE;
          if (overlay != null) {
            icon.setIcon(overlay, 1);
          }
          return icon;
        }
      }
    };
  }
}
