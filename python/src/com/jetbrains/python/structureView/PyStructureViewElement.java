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
package com.jetbrains.python.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.LayeredIcon;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * Handles nodes in Structure View.
 * @author yole
 */
public class PyStructureViewElement implements StructureViewTreeElement {

  protected enum Visibility {
    NORMAL, // visible
    INVISIBLE, // not visible: e.g. local to function
    PRIVATE,  // "__foo" in a class
    PROTECTED, // "_foo"
    PREDEFINED // like "__init__"; only if really visible
  }

  private PyElement myElement;
  private Visibility myVisibility;
  private Icon myIcon;
  private boolean myInherited;
  private boolean myField;

  protected PyStructureViewElement(PyElement element, Visibility visibility, boolean inherited, boolean field) {
    myElement = element;
    myVisibility = visibility;
    myInherited = inherited;
    myField = field;
  }

  public PyStructureViewElement(PyElement element) {
    this(element, Visibility.NORMAL, false, false);
  }

  protected StructureViewTreeElement createChild(PyElement element, Visibility visibility, boolean inherited, boolean field) {
    return new PyStructureViewElement(element, visibility, inherited, field);
  }

  @Override
  public PyElement getValue() {
    return myElement;
  }

  public boolean isInherited() {
    return myInherited;
  }

  public boolean isField() {
    return myField;
  }

  @Override
  public void navigate(boolean requestFocus) {
    myElement.navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return myElement.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return myElement.canNavigateToSource();
  }

  public void setIcon(Icon icon) {
    myIcon = icon;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof StructureViewTreeElement) {
      final Object value = ((StructureViewTreeElement)o).getValue();
      final String name = myElement.getName();
      if (value instanceof PyElement && name != null) {
        return name.equals(((PyElement)value).getName());
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    final String name = myElement.getName();
    return name != null ? name.hashCode() : 0;
  }

  @NotNull
  public StructureViewTreeElement[] getChildren() {
    final Collection<StructureViewTreeElement> children = new ArrayList<>();
    for (PyElement e : getElementChildren(myElement)) {
      children.add(createChild(e, getElementVisibility(e), false, elementIsField(e)));
    }
    PyPsiUtils.assertValid(myElement);
    if (myElement instanceof PyClass) {
      for (PyClass c : ((PyClass)myElement).getAncestorClasses(null)) {
        for (PyElement e: getElementChildren(c)) {
          final StructureViewTreeElement inherited = createChild(e, getElementVisibility(e), true, elementIsField(e));
          if (!children.contains(inherited)) {
            children.add(inherited);
          }
        }
      }
    }
    return children.toArray(new StructureViewTreeElement[children.size()]);
  }

  protected boolean elementIsField(PyElement element) {
    return element instanceof PyTargetExpression && PsiTreeUtil.getParentOfType(element, PyClass.class) != null;
  }

  private static Visibility getElementVisibility(PyElement element) {
    if (!(element instanceof PyTargetExpression) && PsiTreeUtil.getParentOfType(element, PyFunction.class) != null) {
      return Visibility.INVISIBLE;
    }
    else {
      return getVisibilityByName(element.getName());
    }
  }

  private Collection<PyElement> getElementChildren(final PyElement element) {
    final Collection<PyElement> children = new ArrayList<>();
    PyPsiUtils.assertValid(element);
    element.acceptChildren(new PyElementVisitor() {
      @Override
      public void visitElement(PsiElement e) {
        if (isWorthyItem(e, element)) {
          children.add((PyElement)e);
        }
        else {
          e.acceptChildren(this);
        }
      }
    });
    if (element instanceof PyClass) {
      final List<PyElement> attrs = getClassAttributes((PyClass)element);
      final List<PyElement> filtered = new ArrayList<>();
      final Set<String> names = new HashSet<>();
      for (PyElement attr : attrs) {
        final String name = attr.getName();
        PyPsiUtils.assertValid(attr);
        if (!names.contains(name)) {
          filtered.add(attr);
        }
        names.add(name);
      }
      final Comparator<PyElement> comparator = (e1, e2) -> {
        final String n1 = e1.getName();
        final String n2 = e2.getName();
        return (n1 != null && n2 != null) ? n1.compareTo(n2) : 0;
      };
      Collections.sort(filtered, comparator);
      children.addAll(filtered);
    }
    return children;
  }

  protected List<PyElement> getClassAttributes(PyClass cls) {
    final List<PyElement> results = new ArrayList<>();
    results.addAll(cls.getInstanceAttributes());
    results.addAll(cls.getClassAttributes());
    return results;
  }

  private static Visibility getVisibilityByName(@Nullable String name) {
    if (name != null) {
      if (name.startsWith("__")) {
        if (PyNames.UnderscoredAttributes.contains(name)) {
          return Visibility.PREDEFINED;
        }
        else {
          return Visibility.PRIVATE;
        }
      }
      else if (name.startsWith("_")) {
        return Visibility.PROTECTED;
      }
    }
    return Visibility.NORMAL;
  }

  protected boolean isWorthyItem(@Nullable PsiElement element, @Nullable PsiElement parent) {
    if (element instanceof PyClass || element instanceof PyFunction) {
      return true;
    }
    if (!(parent instanceof PyClass) && (element instanceof PyTargetExpression) && !((PyTargetExpression)element).isQualified()) {
      PsiElement e = element.getParent();
      if (e instanceof PyAssignmentStatement) {
        e = e.getParent();
        if (e instanceof PyFile) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  @Override
  public ItemPresentation getPresentation() {
    final ItemPresentation presentation = myElement.getPresentation();
    return new ColoredItemPresentation() {
      @Nullable
      @Override
      public String getPresentableText() {
        if (myElement instanceof PyFile) {
          return myElement.getName();
        }
        return presentation != null ? presentation.getPresentableText() : PyNames.UNNAMED_ELEMENT;
      }

      @Nullable
      @Override
      public TextAttributesKey getTextAttributesKey() {
        if (isInherited()) {
          return CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES;
        }
        return null;
      }

      @Nullable
      @Override
      public String getLocationString() {
        return null;
      }

      @Nullable
      @Override
      public Icon getIcon(boolean open) {
        Icon normal_icon = myElement.getIcon(0);
        if (myIcon != null) normal_icon = myIcon; // override normal
        if (myVisibility == Visibility.NORMAL) {
          return normal_icon;
        }
        else {
          LayeredIcon icon = new LayeredIcon(2);
          icon.setIcon(normal_icon, 0);
          Icon overlay = null;
          if (myVisibility == Visibility.PRIVATE || myVisibility == Visibility.PROTECTED) {
            overlay = PythonIcons.Python.Nodes.Lock;
          }
          else if (myVisibility == Visibility.PREDEFINED) {
            overlay = PythonIcons.Python.Nodes.Cyan_dot;
          }
          else if (myVisibility == Visibility.INVISIBLE) {
            overlay = PythonIcons.Python.Nodes.Red_inv_triangle;
          }
          if (overlay != null) {
            icon.setIcon(overlay, 1);
          }
          return icon;
        }
      }
    };
  }
}
