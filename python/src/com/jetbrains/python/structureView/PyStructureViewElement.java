package com.jetbrains.python.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.Function;
import com.jetbrains.python.PyIcons;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.openapi.util.text.StringUtil.notNullize;

/**
 * Handles nodes in Structure View.
 * @author yole
 */
public class PyStructureViewElement implements StructureViewTreeElement {

  private enum Visibility {
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

  public PyStructureViewElement(PyElement element, Visibility vis, boolean inherited, boolean field) {
    myElement = element;
    myVisibility = vis;
    myInherited = inherited;
    myField = field;
  }

  public PyStructureViewElement(PyElement element) {
    this(element, Visibility.NORMAL, false, false);
  }

  public PyElement getValue() {
    return myElement;
  }

  public boolean isInherited() {
    return myInherited;
  }

  public boolean isField() {
    return myField;
  }

  public void navigate(boolean requestFocus) {
    myElement.navigate(requestFocus);
  }

  public boolean canNavigate() {
    return myElement.canNavigate();
  }

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

  public StructureViewTreeElement[] getChildren() {
    final Collection<StructureViewTreeElement> children = new LinkedHashSet<StructureViewTreeElement>();
    for (PyElement e : getElementChildren(myElement)) {
      children.add(new PyStructureViewElement(e, getElementVisibility(e), false, elementIsField(e)));
    }
    if (myElement instanceof PyClass) {
      for (PyClass c : ((PyClass)myElement).iterateAncestorClasses()) {
        for (PyElement e: getElementChildren(c)) {
          children.add(new PyStructureViewElement(e, getElementVisibility(e), true, elementIsField(e)));
        }
      }
    }
    return children.toArray(new StructureViewTreeElement[children.size()]);
  }

  private static boolean elementIsField(PyElement element) {
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

  private static Collection<PyElement> getElementChildren(final PyElement element) {
    final Collection<PyElement> children = new ArrayList<PyElement>();
    element.acceptChildren(new PyElementVisitor() {
      @Override
      public void visitElement(PsiElement e) {
        if (e instanceof PyClass || e instanceof PyFunction ||
            (!(element instanceof PyClass) && isWorthyItem(e))) {
          children.add((PyElement)e);
        }
        else {
          e.acceptChildren(this);
        }
      }
    });
    final Collection<PyTargetExpression> attrs = new ArrayList<PyTargetExpression>();
    if (element instanceof PyClass) {
      final PyClass c = (PyClass)element;
      final Comparator<PyTargetExpression> comparator = new Comparator<PyTargetExpression>() {
        @Override
        public int compare(PyTargetExpression e1, PyTargetExpression e2) {
          final String n1 = e1.getName();
          final String n2 = e2.getName();
          return (n1 != null && n2 != null) ? n1.compareTo(n2) : 0;
        }
      };
      final List<PyTargetExpression> instanceAttrs = c.getInstanceAttributes();
      final List<PyTargetExpression> classAttrs = c.getClassAttributes();
      Collections.sort(instanceAttrs, comparator);
      Collections.sort(classAttrs, comparator);
      attrs.addAll(classAttrs);
      attrs.addAll(instanceAttrs);
    }
    for (PyTargetExpression e : attrs) {
      if (e.isValid()) {
        children.add(e);
      }
    }
    return children;
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

  private static boolean isWorthyItem(PsiElement element) {
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
        else if (e instanceof PyFile) {
          return true;
        }
      }
    }
    return false;
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        final String unnamed = "<unnamed>";
        if (myElement instanceof PyFunction) {
          PyParameterList argList = ((PyFunction) myElement).getParameterList();
          StringBuilder result = new StringBuilder(notNullize(myElement.getName(), unnamed));
          ParamHelper.appendParameterList(argList, result);
          return result.toString();
        }
        else if (myElement instanceof PyClass && myElement.isValid()) {
          PyClass c = (PyClass) myElement;
          StringBuilder result = new StringBuilder(notNullize(c.getName(), unnamed));
          PyExpression[] superClassExpressions = c.getSuperClassExpressions();
          if (superClassExpressions.length > 0) {
            result.append("(");
            result.append(join(Arrays.asList(superClassExpressions), new Function<PyExpression, String>() {
              public String fun(PyExpression expr) {
                String name = expr.getText();
                return notNullize(name, unnamed);
              }
            }, ", "));
            result.append(")");
          }
          return result.toString();
        }
        return myElement.getName();
      }

      @Nullable
      public TextAttributesKey getTextAttributesKey() {
        if (isInherited()) {
          return CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES;
        }
        return null;
      }

      @Nullable
      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        Icon normal_icon = myElement.getIcon(Iconable.ICON_FLAG_OPEN);
        if (myIcon != null) normal_icon = myIcon; // override normal
        if (myVisibility == Visibility.NORMAL) {
          return normal_icon;
        }
        else {
          LayeredIcon icon = new LayeredIcon(2);
          icon.setIcon(normal_icon, 0);
          Icon overlay = null;
          if (myVisibility == Visibility.PRIVATE || myVisibility == Visibility.PROTECTED) {
            overlay = PyIcons.PRIVATE;
          }
          else if (myVisibility == Visibility.PREDEFINED) {
            overlay = PyIcons.PREDEFINED;
          }
          else if (myVisibility == Visibility.INVISIBLE) {
            overlay = PyIcons.INVISIBLE;
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
