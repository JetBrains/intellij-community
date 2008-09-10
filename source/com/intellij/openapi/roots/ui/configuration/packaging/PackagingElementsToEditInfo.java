package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.deployment.ContainerElement;
import com.intellij.openapi.deployment.PackagingMethod;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class PackagingElementsToEditInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.packaging.PackagingElementsToEditInfo");
  private final List<ContainerElement> myElements;
  private final PackagingMethod myPackagingMethod;
  private final String myRelativePath;
  private final PackagingMethod[] myAllowedPackagingMethods;
  private final String myElementText;
  private Map<ContainerElement, String> myPathTails;

  public PackagingElementsToEditInfo(final ContainerElement element, @NotNull PackagingEditorPolicy policy) {
    myElements = Collections.singletonList(element);
    myRelativePath = policy.isRelativePathCellEditable(element) ? element.getURI() : null;
    myPackagingMethod = element.getPackagingMethod();
    myAllowedPackagingMethods = policy.getAllowedPackagingMethods(element);
    myElementText = policy.getElementText(element);
  }

  public PackagingElementsToEditInfo(final Set<ContainerElement> elements, @NotNull PackagingEditorPolicy policy) {
    LOG.assertTrue(elements.size() > 1);
    myElements = new ArrayList<ContainerElement>(elements);
    myPackagingMethod = getCommonPackagingMethod(elements);
    String commonPath = getCommonRelativePath(elements, policy);
    if (commonPath == null) {
      final Map<ContainerElement, String> elementsToPaths = new HashMap<ContainerElement, String>();
      for (ContainerElement element : elements) {
        elementsToPaths.put(element, element.getURI());
      }
      Pair<String, Map<ContainerElement, String>> pair = getPrefixAndSuffixes(elementsToPaths, policy);
      if (pair != null) {
        commonPath = pair.getFirst();
        myPathTails = pair.getSecond();
      }
    }
    myRelativePath = commonPath;
    myAllowedPackagingMethods = getAllowedPackagingMethods(elements, policy);
    myElementText = ProjectBundle.message("element.description.0.items", elements.size());
  }

  public List<ContainerElement> getElements() {
    return myElements;
  }

  @Nullable
  public PackagingMethod getPackagingMethod() {
    return myPackagingMethod;
  }

  @Nullable
  public String getRelativePath() {
    return myRelativePath;
  }

  @Nullable
  public Map<ContainerElement, String> getPathTails() {
    return myPathTails;
  }

  @NotNull
  public PackagingMethod[] getAllowedPackagingMethods() {
    return myAllowedPackagingMethods;
  }

  public String getElementText() {
    return myElementText;
  }

  @NotNull
  private static PackagingMethod[] getAllowedPackagingMethods(final Set<ContainerElement> elements, final PackagingEditorPolicy policy) {
    List<PackagingMethod> methods = null;
    for (ContainerElement element : elements) {
      List<PackagingMethod> otherMethods = Arrays.asList(policy.getAllowedPackagingMethods(element));
      if (methods == null) {
        methods = new ArrayList<PackagingMethod>(otherMethods);
      }
      else {
        methods.retainAll(otherMethods);
      }
    }
    return methods != null ? methods.toArray(new PackagingMethod[methods.size()]) : PackagingMethod.EMPTY_ARRAY;
  }

  @Nullable
  public static Pair<String, Map<ContainerElement, String>> getPrefixAndSuffixes(final Map<ContainerElement, String> elementsToPaths, final PackagingEditorPolicy policy) {
    String prefix = null;
    for (Map.Entry<ContainerElement,String> entry : elementsToPaths.entrySet()) {
      if (!policy.isRelativePathCellEditable(entry.getKey())) {
        return null;
      }
      String path = entry.getValue();
      if (prefix == null) {
        prefix = path;
      }
      else {
        prefix = StringUtil.commonPrefix(prefix, path);
        if (!path.equals(prefix) && !path.startsWith(prefix + "/")) {
          int i = prefix.lastIndexOf('/');
          prefix = i != -1 ? prefix.substring(0, i) : "";
        }
      }
    }
    if (prefix == null) {
      return null;
    }
    Map<ContainerElement, String> map = new HashMap<ContainerElement, String>();
    for (Map.Entry<ContainerElement,String> entry : elementsToPaths.entrySet()) {
      String path = entry.getValue();
      map.put(entry.getKey(), path.substring(prefix.length()));
    }
    return Pair.create(prefix, map);
  }

  @Nullable
  private static String getCommonRelativePath(final Set<ContainerElement> elements, final PackagingEditorPolicy policy) {
    String path = null;
    for (ContainerElement element : elements) {
      if (!policy.isRelativePathCellEditable(element)) {
        return null;
      }
      if (path == null) {
        path = element.getURI();
      }
      else if (!path.equals(element.getURI())) {
        return null;
      }
    }
    return path;
  }

  @Nullable
  private static PackagingMethod getCommonPackagingMethod(final Set<ContainerElement> elements) {
    PackagingMethod method = null;
    for (ContainerElement element : elements) {
      if (method == null) {
        method = element.getPackagingMethod();
      }
      else if (!method.equals(element.getPackagingMethod())) {
        return null;
      }
    }
    return method;
  }
}
