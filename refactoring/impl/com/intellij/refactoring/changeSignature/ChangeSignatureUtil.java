package com.intellij.refactoring.changeSignature;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public class ChangeSignatureUtil {
  private ChangeSignatureUtil() {}

  public static <Parent extends PsiElement, Child extends PsiElement>
  void synchronizeList(Parent list, final List<Child> newElements, ChildrenGenerator<Parent, Child> generator, final boolean[] shouldRemoveChild)
    throws IncorrectOperationException {

    ArrayList<Child> elementsToRemove = null;
    List<Child> elements;

    int index = 0;
    while (true) {
      elements = generator.getChildren(list);
      if (index == newElements.size()) break;

      if (elementsToRemove == null) {
        elementsToRemove = new ArrayList<Child>();
        for (int i = 0; i < shouldRemoveChild.length; i++) {
          if (shouldRemoveChild[i]) {
            elementsToRemove.add(elements.get(i));
          }
        }
      }

      Child oldElement = index < elements.size() ? elements.get(index) : null;
      Child newElement = newElements.get(index);
      if (!newElement.equals(oldElement)) {
        if (oldElement != null && elementsToRemove.contains(oldElement)) {
          oldElement.delete();
          index--;
        }
        else {
          assert list.isWritable() : PsiUtil.getVirtualFile(list);
          list.addBefore(newElement, oldElement);
          if (list.equals(newElement.getParent())) {
            newElement.delete();
          }
        }
      }
      index++;
    }
    for (int i = newElements.size(); i < elements.size(); i++) {
      Child element = elements.get(i);
      element.delete();
    }
  }

  public static interface ChildrenGenerator<Parent extends PsiElement, Child extends PsiElement> {
    List<Child> getChildren(Parent parent);
  }
}
