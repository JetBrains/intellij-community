package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.ui.popup.ListItemDescriptor;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SidePanel extends JPanel {

  private JList myList;
  private DefaultListModel myModel;
  private Place.Navigator myNavigator;
  private ArrayList<Place> myPlaces = new ArrayList<Place>();

  private Map<Integer, String> myIndex2Separator = new HashMap<Integer, String>();
  private Map<Place, Presentation> myPlace2Presentation = new HashMap<Place, Presentation>();
  private History myHistory;

  public SidePanel(Place.Navigator navigator, History history) {
    myHistory = history;
    myNavigator = navigator;

    setLayout(new BorderLayout());

    myModel = new DefaultListModel();
    myList = new JList(myModel);

    final ListItemDescriptor descriptor = new ListItemDescriptor() {
      public String getTextFor(final Object value) {
        return myPlace2Presentation.get(value).getText();
      }

      public String getTooltipFor(final Object value) {
        return getTextFor(value);
      }

      public Icon getIconFor(final Object value) {
        return null;
        //return myPlace2Presentation.get(value).getIcon();
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
        if (e.getValueIsAdjusting()) return;
        final Object value = myList.getSelectedValue();
        myNavigator.navigateTo(((Place)value), false);
      }
    });
  }

  public void addPlace(Place place, @NotNull Presentation presentation) {
    myModel.addElement(place);
    myPlaces.add(place);
    myPlace2Presentation.put(place, presentation);
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
