package com.intellij.facet.impl.ui.libraries;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.util.OrderEntryCellAppearanceUtils;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.Icons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class ChooseLibrariesDialog extends DialogWrapper {
  private LibraryElementChooser myChooser;

  public ChooseLibrariesDialog(final Component parent, final List<Library> libraries) {
    super(parent, true);
    setTitle(ProjectBundle.message("dialog.title.select.libraries"));
    setModal(true);
    myChooser = new LibraryElementChooser(libraries);
    init();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myChooser;
  }

  public void markElements(final Collection<Library> elements) {
    myChooser.markElements(elements);
  }

  public List<Library> getMarkedLibraries() {
    return myChooser.getMarkedElements();
  }

  private static class LibraryElementChooser extends ElementsChooser<Library> {
    private LibraryElementChooser(final List<Library> elements) {
      super(elements, false);
    }

    protected Icon getItemIcon(final Library value) {
      return Icons.LIBRARY_ICON;
    }

    protected String getItemText(final Library value) {
      return OrderEntryCellAppearanceUtils.forLibrary(value).getText();
    }
  }
}
