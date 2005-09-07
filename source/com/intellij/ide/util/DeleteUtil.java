package com.intellij.ide.util;

import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;

/**
 * @author dsl
 */
public class DeleteUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.DeleteUtil");
  public static void appendMessage(int count, String singleName, String multipleName, StringBuffer buffer) {
    if (count > 0) {
      if (buffer.length() > 0) {
        buffer.append(" and ");
      }
      buffer.append(count);
      buffer.append(' ');
      if (count == 1) {
        buffer.append(singleName);
      }
      else {
        buffer.append(multipleName);
      }
    }
  }

  public static PsiElement[] filterElements(PsiElement[] elements) {
    if (LOG.isDebugEnabled()) {
      for (PsiElement element : elements) {
        LOG.debug("element = " + element);
      }
    }

    ArrayList<PsiElement> filteredElements = new ArrayList<PsiElement>();
    for (PsiElement element : elements) {
      filteredElements.add(element);
    }

    int previousSize;
    do {
      previousSize = filteredElements.size();
      outer:
      for (PsiElement element : filteredElements) {
        for (PsiElement element2 : filteredElements) {
          if (element == element2) continue;
          if (PsiTreeUtil.isAncestor(element, element2, false)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("removing " + element2);
            }
            filteredElements.remove(element2);
            break outer;
          }
        }
      }
    } while (filteredElements.size() != previousSize);

    if (LOG.isDebugEnabled()) {
      for (PsiElement element : filteredElements) {
        LOG.debug("filtered element = " + element);
      }
    }

    return filteredElements.toArray(new PsiElement[filteredElements.size()]);
  }

  public static String generateWarningMessage(String actionName, final PsiElement[] elements) {
    int methods = 0;
    int fields = 0;
    int interfaces = 0;
    int classes = 0;
    int files = 0;
    int directories = 0;
    int packages = 0;
    int packageDirectories = 0;
    int customElements = 0;
    String[] objName = new String[] { "", "", "" };

    for (final PsiElement elementToDelete : elements) {
      if (elementToDelete instanceof Property) {
        objName[0] = ((Property)elementToDelete).getName();
        objName[1] = "property";
      }
      else if (elementToDelete instanceof PsiMethod) {
        objName[0] = ((PsiMethod)elementToDelete).getName();
        objName[1] = "method";
        methods++;
      }
      else if (elementToDelete instanceof PsiField) {
        objName[0] = ((PsiField)elementToDelete).getName();
        objName[1] = "field";
        fields++;
      }
      else if (elementToDelete instanceof PsiClass) {
        objName[0] = ((PsiClass)elementToDelete).getName();
        if (((PsiClass)elementToDelete).isInterface()) {
          objName[1] = "interface";
          interfaces++;
        }
        else {
          objName[1] = elementToDelete instanceof PsiTypeParameter ? "type parameter" : "class";
          classes++;
        }
      }
      else if (elementToDelete instanceof PsiFile) {
        objName[0] = ((PsiFile)elementToDelete).getName();
        objName[1] = "file";
        files++;
      }
      else if (elementToDelete instanceof PsiDirectory) {
        directories = processDirectory(elementToDelete, objName, directories);
      }
      else if (elementToDelete instanceof PsiPackage) {
        final PsiPackage psiPackage = (PsiPackage)elementToDelete;
        final String name = psiPackage.getName();
        final PsiDirectory[] psiDirectories = psiPackage.getDirectories();
        final int count = psiDirectories.length;
        objName[1] = "package";
        objName[0] = name;
        objName[2] = " " + buildDirectoryMessage(count);
        packages += 1;
        packageDirectories += count;
      }
      else if (elementToDelete instanceof PsiNamedElement) {
        objName[0] = ((PsiNamedElement) elementToDelete).getName();
        objName[1] = elementToDelete.getLanguage().getFindUsagesProvider().getType(elementToDelete);
        customElements++;
      }
    }

    String warningMessage = actionName + " ";
    if (elements.length == 1) {
      warningMessage += objName[1] + " \"" + objName[0] + "\"" + objName[2] + "?";
    }
    else {
      StringBuffer buffer = new StringBuffer();
      appendMessage(directories, "directory", "directories", buffer);
      appendMessage(files, "file", "files", buffer);
      appendMessage(classes, "class", "classes", buffer);
      appendMessage(interfaces, "interface", "interfaces", buffer);
      appendMessage(methods, "method", "methods", buffer);
      appendMessage(fields, "field", "fields", buffer);
      if (packages > 0) {
        appendMessage(packages, "package", "packages", buffer);
        buffer.append(' ');
        buffer.append(buildDirectoryMessage(packageDirectories));
      }
      appendMessage(customElements, "element", "elements", buffer);
      buffer.append('?');
      warningMessage += buffer.toString();
    }
    return warningMessage;
  }

  private static String buildDirectoryMessage(final int count) {
    return "(" + count + " " + (count == 1 ? "directory" : "directories") + ")";
  }

  private static int processDirectory(final PsiElement elementToDelete, String[] objName, int directories) {
    objName[0] = ((PsiDirectory)elementToDelete).getName();
    objName[1] = "directory";
    directories++;
    return directories;
  }
}
