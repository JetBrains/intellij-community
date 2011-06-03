package com.jetbrains.python.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.Function;
import com.intellij.util.Icons;
import com.jetbrains.python.PyIcons;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
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
        if (element instanceof PyClass || element instanceof PyFunction ||
            (!(myElement instanceof PyClass) && isWorthyItem(element))) {
          childrenElements.add((PyElement)element);
        }
        else {
          element.acceptChildren(this);
        }
      }
    });
    final Collection<StructureViewTreeElement> children = new ArrayList<StructureViewTreeElement>();
    for (PyElement element : childrenElements) {
      final Visibility vis;
      if (PsiTreeUtil.getParentOfType(element, PyFunction.class) != null) {
        // whatever is defined inside a def, is hidden
        vis = Visibility.INVISIBLE;
      }
      else {
        vis = getVisibilityByName(element.getName());
      }
      final PyStructureViewElement e = new PyStructureViewElement(element, vis);
      children.add(e);
      if (element instanceof PyClass && element.isValid()) {
        PyClass the_exception = PyBuiltinCache.getInstance(element).getClass("Exception");
        final PyClass cls = (PyClass)element;
        for (PyClass anc : cls.iterateAncestorClasses()) {
          if (anc == the_exception) {
            e.setIcon(Icons.EXCEPTION_CLASS_ICON);
            break;
          }
        }
      }
    }
    final Collection<PyTargetExpression> attrs = new ArrayList<PyTargetExpression>();
    if (myElement instanceof PyClass) {
      final PyClass c = (PyClass)myElement;
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
        children.add(new PyStructureViewElement(e, getVisibilityByName(e.getName())));
      }
    }
    return children.toArray(new StructureViewTreeElement[children.size()]);
  }

  private static Visibility getVisibilityByName(@Nullable String name) {
    if (name != null && name.startsWith("__")) {
      if (PyNames.UnderscoredAttributes.contains(name)) {
        return Visibility.PREDEFINED;
      }
      else {
        return Visibility.PRIVATE;
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
