package com.intellij.ide.util.importProject;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Icons;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 16, 2007
 */
public class ModulesLayoutPanel extends ProjectLayoutPanel<ModuleDescriptor>{
  private static final Icon ICON_MODULE = IconLoader.getIcon("/nodes/ModuleClosed.png");

  public ModulesLayoutPanel(ModuleInsight insight) {
    super(insight);
  }

  protected Icon getElementIcon(final Object element) {
    if (element instanceof ModuleDescriptor) {
      return ICON_MODULE;
    }
    if (element instanceof LibraryDescriptor) {
      final LibraryDescriptor libDescr = (LibraryDescriptor)element;
      final Collection<File> jars = libDescr.getJars();
      if (jars.size() == 1) {
        return Icons.JAR_ICON;
      }
      return Icons.LIBRARY_ICON;
    }
    if (element instanceof File) {
      return Icons.JAR_ICON;
    }
    return super.getElementIcon(element);
  }

  protected int getWeight(final Object element) {
    if (element instanceof File) {
      return 10;
    }
    if (element instanceof ModuleDescriptor) {
      return 20;
    }
    if (element instanceof LibraryDescriptor) {
      return ((LibraryDescriptor)element).getJars().size() > 1? 30 : 40;
    }
    return Integer.MAX_VALUE;
  }

  protected String getElementName(final ModuleDescriptor entry) {
    return entry.getName();
  }

  protected void setElementName(final ModuleDescriptor entry, final String name) {
    entry.setName(name);
  }

  protected String getElementText(final Object element) {
    if (element instanceof ModuleDescriptor) {
      final ModuleDescriptor moduleDescriptor = (ModuleDescriptor)element;
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        builder.append(moduleDescriptor.getName());
        
        final Set<File> contents = moduleDescriptor.getContentRoots();
        final int rootCount = contents.size();
        if (rootCount > 0) {
          builder.append(" (");
          builder.append(contents.iterator().next().getPath());
          if (rootCount > 1) {
            builder.append("...");
          }
          builder.append(")");
        }

        final Set<File> sourceRoots = moduleDescriptor.getSourceRoots();
        if (sourceRoots.size() > 0) {
          builder.append(" [");
          for (Iterator<File> it = sourceRoots.iterator(); it.hasNext();) {
            File root = it.next();
            builder.append(root.getName());
            if (it.hasNext()) {
              builder.append(",");
            }
          }
          builder.append("]");
        }
        return builder.toString();
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
    }
    if (element instanceof LibraryDescriptor) {
      final LibraryDescriptor libDescr = (LibraryDescriptor)element;
      final Collection<File> jars = libDescr.getJars();
      if (jars.size() == 1) {
        return getDisplayText(jars.iterator().next());
      }
      return libDescr.getName();
    }
    return "";
  }

  protected List<ModuleDescriptor> getEntries() {
    final List<ModuleDescriptor> modules = getInsight().getSuggestedModules();
    return modules != null? modules : Collections.<ModuleDescriptor>emptyList();
  }

  protected Collection getDependencies(final ModuleDescriptor entry) {
    final List deps = new ArrayList();
    deps.addAll(entry.getDependencies());
    deps.addAll(getInsight().getLibraryDependencies(entry));
    return deps;
  }

  @Nullable
  protected ModuleDescriptor merge(final List<ModuleDescriptor> entries) {
    final ModuleInsight insight = getInsight();
    ModuleDescriptor mainDescr = null;
    for (ModuleDescriptor entry : entries) {
      if (mainDescr == null) {
        mainDescr = entry;
      }
      else {
        insight.merge(mainDescr, entry);
      }
    }
    return mainDescr;
  }

  protected ModuleDescriptor split(final ModuleDescriptor entry, final String newEntryName, final Set<File> extractedData) {
    return getInsight().splitModule(entry, newEntryName, extractedData);
  }

  protected Collection<File> getContent(final ModuleDescriptor entry) {
    return entry.getContentRoots();
  }
}
