package com.jetbrains.python;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.python.index.PyClassIndex;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class PyGotoClassContributor implements ChooseByNameContributor {
  private static final Icon CLASS_ICON = IconLoader.getIcon("/nodes/class.png");

  public String[] getNames(final Project project, final boolean includeNonProjectItems) {
    final Collection<String> classNames = PyClassIndex.getClassNames();
    return classNames.toArray(new String[classNames.size()]);
  }

  public NavigationItem[] getItemsByName(final String name, final String pattern, final Project project, final boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems ? null : GlobalSearchScope.projectScope(project);
    final List<NavigationItem> result = new ArrayList<NavigationItem>();
    final List<Pair<VirtualFile,PyClassIndex.Entry>> pairs = collectFilesAndValues(name, project, scope);
    for(Pair<VirtualFile, PyClassIndex.Entry> e: pairs) {
      result.add(new PyClassNavigationItem(project, name, e.first, e.second.offset));
    }
    return result.toArray(new NavigationItem[result.size()]);
  }

  private static List<Pair<VirtualFile,PyClassIndex.Entry>> collectFilesAndValues(final String key, final Project project, @Nullable final GlobalSearchScope scope) {
    final List<Pair<VirtualFile, PyClassIndex.Entry>> result = new ArrayList<Pair<VirtualFile,PyClassIndex.Entry>>();
    FileBasedIndex.getInstance().processValues(PyClassIndex.INDEX_ID, key, project, null, new FileBasedIndex.ValueProcessor<List<PyClassIndex.Entry>>() {
      public void process(final VirtualFile file, final List<PyClassIndex.Entry> value) {
        if (scope == null || scope.contains(file)) {
          for(PyClassIndex.Entry e: value) {
            result.add(new Pair<VirtualFile,PyClassIndex.Entry>(file, e));                  
          }
        }
      }
    });
    return result;
  }

  private static class PyClassNavigationItem implements NavigationItem {
    private OpenFileDescriptor myDescriptor;
    private String myName;

    private PyClassNavigationItem(final Project project, final String name, final VirtualFile file, final int offset) {
      myName = name;
      myDescriptor = new OpenFileDescriptor(project, file, offset);
    }

    public String getName() {
      return myName;
    }

    public ItemPresentation getPresentation() {
      return new ItemPresentation() {
        public String getPresentableText() {
          return myName;
        }

        public String getLocationString() {
          return "(" + myDescriptor.getFile().getName() + ")";
        }

        public Icon getIcon(final boolean open) {
          return CLASS_ICON;
        }

        public TextAttributesKey getTextAttributesKey() {
          return null;
        }
      };
    }

    public FileStatus getFileStatus() {
      return FileStatusManager.getInstance(myDescriptor.getProject()).getStatus(myDescriptor.getFile());
    }

    public void navigate(final boolean requestFocus) {
      myDescriptor.navigate(requestFocus);
    }

    public boolean canNavigate() {
      return myDescriptor.canNavigate();
    }

    public boolean canNavigateToSource() {
      return myDescriptor.canNavigateToSource();
    }
  }
}
