package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableEditor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 28
 * @author 2003
 */
public class NamedLibrariesPanel extends JPanel {
  private final ModifiableRootModel myRootModel;
  private final LibraryTable myLibraryTable;
  private final ElementsChooser<LibraryChooserElement> myLibrariesChooser;
  private final ElementsChooser.ElementsMarkListener<LibraryChooserElement> myMarkListener;
  private final JButton myIncludeAllButton;
  private final JButton myExcludeAllButton;
  private final LibraryTable.Listener myLibrariesUpdateListener;

  public NamedLibrariesPanel(ModifiableRootModel rootModel, LibraryTable libraryTable) {
    super(new BorderLayout());

    myRootModel = rootModel;
    myLibraryTable = libraryTable;
    myLibrariesUpdateListener = new LibraryTable.Listener() {
      public void afterLibraryAdded(Library newLibrary) {
        updateChooser(null, true, null);
      }

      public void afterLibraryRenamed(Library library) {
        updateChooser(null, true, null);
      }

      public void beforeLibraryRemoved(Library library) {
      }

      public void afterLibraryRemoved(Library library) {
        updateChooser(null, true, null);
      }
    };
    myLibrariesChooser = new ElementsChooser<LibraryChooserElement>(true);
    myLibrariesChooser.setColorUnmarkedElements(false);

    myMarkListener = new ElementsChooser.ElementsMarkListener<LibraryChooserElement>() {
      public void elementMarkChanged(LibraryChooserElement libraryElement, boolean isMarked) {
        setChooserElementMarked(libraryElement, isMarked);
      }
    };
    myLibrariesChooser.addElementsMarkListener(myMarkListener);

    final JButton editButton = new JButton(ProjectBundle.message("button.edit"));
    myIncludeAllButton = new JButton(ProjectBundle.message("module.libraries.include.all.button"));
    myExcludeAllButton = new JButton(ProjectBundle.message("module.libraries.exclude.all.button"));
    editButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        removeLibraryTableListener();
        try {
          final HashSet<Library> librariesBeforeEdit = new HashSet<Library>(Arrays.asList(myLibraryTable.getLibraries()));
          final List<Library> selection = new ArrayList<Library>(Arrays.asList(convertToLibraries(myLibrariesChooser.getSelectedElements())));
          final boolean isOk = LibraryTableEditor.showEditDialog(editButton, myLibraryTable, selection);
          final Set<Library> librariesAfterEdit = new HashSet<Library>(Arrays.asList(myLibraryTable.getLibraries()));
          librariesAfterEdit.removeAll(librariesBeforeEdit);
          if (isOk) {
            updateChooser(librariesAfterEdit, !isOk, selection);
          }
          else {
            updateChooser(librariesAfterEdit, !isOk, null);
          }
        }
        finally {
          attachLibraryTableListener();
        }
      }
    });
    myIncludeAllButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myLibrariesChooser.setAllElementsMarked(true);
      }
    });
    myExcludeAllButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myLibrariesChooser.setAllElementsMarked(false);
      }
    });

    final JPanel buttonsPanel = new JPanel(new GridBagLayout());
    buttonsPanel.add(editButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 2, 5, 0), 0, 0));
    buttonsPanel.add(myIncludeAllButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 2, 5, 0), 0, 0));
    buttonsPanel.add(myExcludeAllButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 2, 0, 0), 0, 0));

    add(myLibrariesChooser, BorderLayout.CENTER);
    add(buttonsPanel, BorderLayout.EAST);

    updateChooser(null, false, null);
    attachLibraryTableListener();
  }

  private Library[] convertToLibraries(final List<LibraryChooserElement> selectedElements) {
    List<Library> libs = new ArrayList<Library>();
    for (final LibraryChooserElement selectedElement : selectedElements) {
      final LibraryChooserElement chooserElement = (LibraryChooserElement)selectedElement;
      final Library library = chooserElement.getLibrary();
      if (library != null) {
        libs.add(library);
      }
    }
    return libs.toArray(new Library[libs.size()]);
  }

  private boolean myListenerAdded = false;
  private void attachLibraryTableListener() {
    if (!myListenerAdded) {
      myLibraryTable.addListener(myLibrariesUpdateListener);
      myListenerAdded = true;
    }
  }

  private void removeLibraryTableListener() {
    if (myListenerAdded) {
      myLibraryTable.removeListener(myLibrariesUpdateListener);
      myListenerAdded = false;
    }
  }

  private void updateChooser(Set<Library> librariesToMark, boolean keepUnmarkedInvalidEntries, Collection<Library> librariesToSelect) {
    myLibrariesChooser.saveSelection();
    try {
      myLibrariesChooser.removeElementsMarkListener(myMarkListener);
      final List<LibraryChooserElement> unmarkedInvalidElems = new ArrayList<LibraryChooserElement>();
      if (keepUnmarkedInvalidEntries) {
        final int count = myLibrariesChooser.getElementCount();
        for (int idx = 0; idx < count; idx++) {
          final LibraryChooserElement chooserElement = myLibrariesChooser.getElementAt(idx);
          if (!chooserElement.isValid() && !chooserElement.isAttachedToProject()) {
            unmarkedInvalidElems.add(chooserElement);
          }
        }
      }
      myLibrariesChooser.clear();

      final List<LibraryChooserElement> elements = new ArrayList<LibraryChooserElement>();
      final Library[] libraries = myLibraryTable.getLibraries();
      for (final Library library : libraries) {
        elements.add(new LibraryChooserElement(library, myRootModel.findLibraryOrderEntry(library)));
      }
      final LibraryOrderEntry[] invalidLibraryOrderEntries = getInvalidLibraryOrderEntries();
      for (LibraryOrderEntry entry : invalidLibraryOrderEntries) {
        elements.add(new LibraryChooserElement(null, entry));
      }
      elements.addAll(unmarkedInvalidElems);
      Collections.sort(elements, new Comparator<LibraryChooserElement>() {
        public int compare(LibraryChooserElement elem1, LibraryChooserElement elem2) {
          return elem1.getName().compareToIgnoreCase(elem2.getName());
        }
      });
      List<LibraryChooserElement> elementsToSelect = new ArrayList<LibraryChooserElement>();
      for (final LibraryChooserElement element : elements) {
        LibraryChooserElement chooserElement = (LibraryChooserElement)element;
        if (librariesToMark != null && chooserElement.isValid()) {
          if (librariesToMark.contains(chooserElement.getLibrary())) {
            setChooserElementMarked(chooserElement, true);
          }
        }
        final ElementsChooser.ElementProperties elementProperties = chooserElement.isValid()
                                                                    ? LibraryChooserElement.VALID_LIBRARY_ELEMENT_PROPERTIES
                                                                    : LibraryChooserElement.INVALID_LIBRARY_ELEMENT_PROPERTIES;
        myLibrariesChooser.addElement(chooserElement, chooserElement.isAttachedToProject(), elementProperties);
        if (librariesToSelect != null && librariesToSelect.contains(chooserElement.getLibrary())) {
          elementsToSelect.add(chooserElement);
        }
      }
      if (elementsToSelect.size() > 0) {
        myLibrariesChooser.selectElements(elementsToSelect);
      }

      final int elementCount = myLibrariesChooser.getElementCount();
      myIncludeAllButton.setEnabled(elementCount > 0);
      myExcludeAllButton.setEnabled(elementCount > 0);

      myLibrariesChooser.addElementsMarkListener(myMarkListener);
    }
    finally {
      if (librariesToSelect == null) { // do not restore previous selection if there is already stuff to select
        myLibrariesChooser.restoreSelection();
      }
    }
  }

  protected void setChooserElementMarked(LibraryChooserElement chooserElement, boolean isMarked) {
    if (isMarked) {
      if (!chooserElement.isAttachedToProject()) {
        final LibraryOrderEntry orderEntry = chooserElement.isValid()? myRootModel.addLibraryEntry(chooserElement.getLibrary()) : myRootModel.addInvalidLibrary(chooserElement.getName(), myLibraryTable.getTableLevel());
        chooserElement.setOrderEntry(orderEntry);
      }
    }
    else {
      if (chooserElement.isAttachedToProject()) {
        myRootModel.removeOrderEntry(chooserElement.getOrderEntry());
        chooserElement.setOrderEntry(null);
      }
    }
  }

  private LibraryOrderEntry[] getInvalidLibraryOrderEntries() {
    final OrderEntry[] orderEntries = myRootModel.getOrderEntries();
    ArrayList<LibraryOrderEntry> entries = new ArrayList<LibraryOrderEntry>();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)orderEntry;
        if (!libraryOrderEntry.isValid() && libraryOrderEntry.getLibraryLevel().equals(myLibraryTable.getTableLevel())) {
          entries.add(libraryOrderEntry);
        }
      }
    }
    return entries.toArray(new LibraryOrderEntry[entries.size()]);
  }

  public void disposeUIResources() {
    removeLibraryTableListener();
  }
}


