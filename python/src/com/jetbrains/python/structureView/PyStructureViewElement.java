package com.jetbrains.python.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
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
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Handles nodes in Structure View.
 * @author yole
 */
public class PyStructureViewElement implements StructureViewTreeElement {

  private enum Visibility {
    NORMAL, // visible
    INVISIBLE, // not visible: e.g. local to function
    PRIVATE,  // "__foo" in a class
    PREDEFINED // like "__init__"; only if really visible
  }

  private PyElement myElement;
  private Visibility myVisibility;
  private Icon myIcon;

  public PyStructureViewElement(PyElement element, Visibility vis) {
    myElement = element;
    myVisibility = vis;
  }

  public PyStructureViewElement(PyElement element) {
    this(element, Visibility.NORMAL);
  }

  public PyElement getValue() {
    return myElement;
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

  public StructureViewTreeElement[] getChildren() {
    final Set<PyElement> childrenElements = new LinkedHashSet<PyElement>();
    myElement.acceptChildren(new PyElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (isWorthyClassItem(element)) {
          childrenElements.add((PyElement)element);
        }
        else {
          element.acceptChildren(this);
        }
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
          if (PyNames.UnderscoredAttributes.contains(name)) {
            vis = Visibility.PREDEFINED;
          }
          else {
            vis = Visibility.PRIVATE;
          }
        }
      }
      children[i] = new PyStructureViewElement(element, vis);
      if (element instanceof PyClass && element.isValid()) {
        PyClass the_exception = PyBuiltinCache.getInstance(element).getClass("Exception");
        final PyClass cls = (PyClass)element;
        for (PyClass anc : cls.iterateAncestorClasses()) {
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
        if (myElement instanceof PyFunction) {
          PyParameterList argList = ((PyFunction) myElement).getParameterList();
          final String name = myElement.getName();
          StringBuilder result = new StringBuilder(name == null ? "<unnamed>" : name);
          ParamHelper.appendParameterList(argList, result);
          return result.toString();
        }
        else if (myElement instanceof PyClass) {
          PyClass c = (PyClass) myElement;
          StringBuilder result = new StringBuilder(c.getName());
          PyClass[] superClasses = c.getSuperClasses();
          int n = superClasses.length;
          if (n > 0) {
            result.append("(");
            for (int i = 0; i < n; i++) {
              c = superClasses[i];
              result.append(c.getName());
              if (i != n - 1) {
                result.append(", ");
              }
            }
            result.append(")");
          }
          return result.toString();
        }
        return myElement.getName();
      }

      @Nullable
      public TextAttributesKey getTextAttributesKey() {
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
          if (myVisibility == Visibility.PRIVATE) {
            overlay = PyIcons.PRIVATE;
          }
          else if (myVisibility == Visibility.PREDEFINED) {
            overlay = PyIcons.PREDEFINED;
          }
          else if (myVisibility == Visibility.INVISIBLE) overlay = PyIcons.INVISIBLE;
          if (overlay != null) {
            icon.setIcon(overlay, 1);
          }
          return icon;
        }
      }
    };
  }
}
