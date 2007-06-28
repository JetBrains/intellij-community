package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.popup.ListItemDescriptor;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.*;

public class SidePanel extends JPanel {

  private JList myList;
  private DefaultListModel myModel;
  private ProjectStructureConfigurable myStructure;
  private ArrayList<Place> myPlaces = new ArrayList<Place>();

  private Map<Integer, String> myIndex2Separator = new HashMap<Integer, String>();

  public SidePanel(ProjectStructureConfigurable stucture) {
    myStructure = stucture;

    setLayout(new BorderLayout());

    myModel = new DefaultListModel();
    myList = new JList(myModel);

    final ListItemDescriptor descriptor = new ListItemDescriptor() {
      public String getTextFor(final Object value) {
        return ((Place)value).getPresentation().getText();
      }

      public String getTooltipFor(final Object value) {
        return getTextFor(value);
      }

      public Icon getIconFor(final Object value) {
        return null;
        //return ((Place)value).getPresentation().getIcon();
      }

      public boolean hasSeparatorAboveOf(final Object value) {
        final int index = myPlaces.indexOf(value);
        return myIndex2Separator.get(index) != null;
      }

      public String getCaptionAboveOf(final Object value) {
        return myIndex2Separator.get(myPlaces.indexOf(value));
      }
    };

    myList.setCellRenderer(new GroupedItemsListRenderer(descriptor));


    add(new JScrollPane(myList), BorderLayout.CENTER);
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        if (e.getValueIsAdjusting() || myStructure.isHistoryNavigatedNow()) return;
        final Object value = myList.getSelectedValue();
        if (value == null) {
          myStructure.select(null);
        } else {
          myStructure.select(((Place<NamedConfigurable>)value).getObject());
        }
      }
    });
  }

  public void addPlace(Place place) {
    myModel.addElement(place);
    myPlaces.add(place);
    revalidate();
    repaint();
  }

  public void addSeparator(String text) {
    myIndex2Separator.put(myPlaces.size(), text);
  }

  public Collection<Place> getPlaces() {
    return myPlaces;
  }

  public void select(final Place place) {
    myList.setSelectedValue(place, true);
  }
}
