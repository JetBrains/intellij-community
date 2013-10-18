package com.jetbrains.python.hierarchy;

import com.intellij.ide.hierarchy.HierarchyBrowserManager;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 12, 2009
 * Time: 8:30:36 PM
 */
public class PyHierarchyUtils {
  private static final Comparator<NodeDescriptor> NODE_DESCRIPTOR_COMPARATOR = new Comparator<NodeDescriptor>() {
    public int compare(final NodeDescriptor first, final NodeDescriptor second) {
      return first.getIndex() - second.getIndex();
    }
  };

  private PyHierarchyUtils() {
  }

  @NotNull
  public static Comparator<NodeDescriptor> getComparator(final Project project) {
    if (HierarchyBrowserManager.getInstance(project).getState().SORT_ALPHABETICALLY) {
      return AlphaComparator.INSTANCE;
    }
    else {
      return NODE_DESCRIPTOR_COMPARATOR;
    }
  }
}
