package com.intellij.uiDesigner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.core.GridLayoutManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Decoration layer is over COMPONENT_LAYER (layer where all components are located).
 * It contains all necessary decorators. Decorators are:
 * - special mini-buttons to perform editing of grids (add/remove of columns)
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class ActiveDecorationLayer extends JComponent{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.ActiveDecorationLayer");

  private final GuiEditor myEditor;
  private final HashMap<RadContainer, ArrayList<ActiveSpot>> myHorizontalSpots;
  private final HashMap<RadContainer, ArrayList<ActiveSpot>> myVerticalSpots;
  /**
   * Cache of invalid items which can be reused later for better performance
   */
  private final ArrayList<ActiveSpot> myInvalidSpotCache;

  public ActiveDecorationLayer(final GuiEditor editor) {
    LOG.assertTrue(editor != null);

    myEditor = editor;
    myHorizontalSpots = new HashMap<RadContainer, ArrayList<ActiveSpot>>();
    myVerticalSpots = new HashMap<RadContainer, ArrayList<ActiveSpot>>();
    myInvalidSpotCache = new ArrayList<ActiveSpot>();
  }

  /**
   * Creates new item or return invalid cached item
   */
  private ActiveSpot createSpot(final ArrayList<ActiveSpot> spots){
    final ActiveSpot result;
    if(!myInvalidSpotCache.isEmpty()){
      result = myInvalidSpotCache.remove(myInvalidSpotCache.size() - 1);
    }
    else{
      result = new ActiveSpot(myEditor);
    }
    LOG.assertTrue(!spots.contains(result));
    add(result);
    spots.add(result);
    return result;
  }

  /**
   * Puts specified <code>spot</code> into invalid spot cache
   */
  private void disposeSpot(final ActiveSpot spot){
    remove(spot);
    myInvalidSpotCache.add(spot);
  }

  /**
   * Layouts all items in horizontal dimension
   */
  private void layoutVerticalSpots(){
    // First of all we have to remove all invalid spots
    final RadRootContainer rootContainer = myEditor.getRootContainer();
    for(Iterator<Map.Entry<RadContainer, ArrayList<ActiveSpot>>> i = myVerticalSpots.entrySet().iterator(); i.hasNext();){
      final Map.Entry<RadContainer, ArrayList<ActiveSpot>> entry = i.next();
      final RadContainer container = (RadContainer)FormEditingUtil.findComponent(rootContainer, entry.getKey().getId());
      final ArrayList<ActiveSpot> spots = entry.getValue();
      if(container == null || !container.hasDragger() || !container.isGrid()){
        // If RadContainer was deleted or breaked we need to invalidate all spots
        for(int j = spots.size() - 1; j >= 0; j--){
          disposeSpot(spots.get(j));
        }
        i.remove();
      }
      else{
        // The RadContainer can be valid but number of spots might be wrong.
        // We need to make number of spots the same as number of rows.
        final GridLayoutManager layout = (GridLayoutManager)container.getLayout();
        final int rowCount = layout.getRowCount();
        if(rowCount > spots.size()){ // add necessary spots
          for(int j = rowCount - spots.size() - 1; j >= 0; j--){
            createSpot(spots);
          }
        }
        else if(rowCount < spots.size()){ // remove unnecessary spots
          for(int j = spots.size() - rowCount - 1; j >= 0; j--){
            disposeSpot(spots.remove(j));
          }
        }
      }
    }

    // Now we have to iterate through all items and insert ActiveSpots
    // for components which should have decorations but do not have items yet.
    FormEditingUtil.iterate(
      rootContainer,
      new FormEditingUtil.ComponentVisitor<RadComponent>() {
        public boolean visit(final RadComponent component) {
          if(!(component instanceof RadContainer)){
            return true;
          }
          final RadContainer container = (RadContainer)component;
          if(!container.isGrid() || !container.hasDragger() || myVerticalSpots.containsKey(container)){
            return true;
          }

          final GridLayoutManager layout = (GridLayoutManager)container.getLayout();
          final ArrayList<ActiveSpot> spots = new ArrayList<ActiveSpot>();
          myVerticalSpots.put(container, spots);
          for(int i = layout.getRowCount() -1; i >= 0; i--){
            createSpot(spots);
          }

          return true;
        }
      }
    );

    // Now we are ready to layout all horizontal items
    for(Iterator<Map.Entry<RadContainer, ArrayList<ActiveSpot>>> i = myVerticalSpots.entrySet().iterator(); i.hasNext();){
      final Map.Entry<RadContainer, ArrayList<ActiveSpot>> entry = i.next();
      final RadContainer container = entry.getKey();
      LOG.assertTrue(container.isGrid());
      final GridLayoutManager layout = (GridLayoutManager)container.getLayout();
      final ArrayList<ActiveSpot> spots = entry.getValue();
      LOG.assertTrue(spots.size() == layout.getRowCount());
      final int[] heights = layout.getHeights();
      final int[] ys = layout.getYs();
      final JComponent delegee = container.getDelegee();
      final Point topLeftPoint = SwingUtilities.convertPoint(delegee, 0, 0, ActiveDecorationLayer.this);
      for(int j = heights.length - 1; j >= 0; j--){
        final ActiveSpot spot = spots.get(j);

        spot.setContainer(container);
        spot.setCell(j);
        spot.setOrientation(SwingConstants.VERTICAL);
        spot.setCellSize(heights[j]);
        spot.updateActions();

        final Dimension prefSize = spot.getPreferredSize();
        final int width = prefSize.width;
        final int shift = Math.max(0, heights[j] - prefSize.height)/2;
        spot.setBounds(
          topLeftPoint.x - width,
          topLeftPoint.y + ys[j] + shift,
          width,
          heights[j] - shift
        );
        spot.validate();
      }
    }
  }

  /**
   * Layouts all items in vertical dimension
   */
  private void layoutHorizontalSpots(){
    // First of all we have to remove all invalid spots
    final RadRootContainer rootContainer = myEditor.getRootContainer();
    for(Iterator<Map.Entry<RadContainer, ArrayList<ActiveSpot>>> i = myHorizontalSpots.entrySet().iterator(); i.hasNext();){
      final Map.Entry<RadContainer, ArrayList<ActiveSpot>> entry = i.next();
      final RadContainer container = (RadContainer)FormEditingUtil.findComponent(rootContainer, entry.getKey().getId());
      final ArrayList<ActiveSpot> spots = entry.getValue();
      if(container == null || !container.hasDragger() || !container.isGrid()){
        // If RadContainer was deleted or breaked we need to invalidate all spots
        for(int j = spots.size() - 1; j >= 0; j--){
          disposeSpot(spots.get(j));
        }
        i.remove();
      }
      else{
        // The RadContainer can be valid but number of spots might be wrong.
        // We need to make number of spots the same as number of columns.
        final GridLayoutManager layout = (GridLayoutManager)container.getLayout();
        final int columnCount = layout.getColumnCount();
        if(columnCount > spots.size()){ // add necessary spots
          for(int j = columnCount - spots.size() - 1; j >= 0; j--){
            createSpot(spots);
          }
        }
        else if(columnCount < spots.size()){ // remove unnecessary spots
          for(int j = spots.size() - columnCount - 1; j >= 0; j--){
            disposeSpot(spots.remove(j));
          }
        }
      }
    }

    // Now we have to iterate through all items and insert ActiveSpots
    // for components which should have decorations but do not have items yet.
    FormEditingUtil.iterate(
      rootContainer,
      new FormEditingUtil.ComponentVisitor<RadComponent>() {
        public boolean visit(final RadComponent component) {
          if(!(component instanceof RadContainer)){
            return true;
          }
          final RadContainer container = (RadContainer)component;
          if(!container.isGrid() || !container.hasDragger() || myHorizontalSpots.containsKey(container)){
            return true;
          }

          final GridLayoutManager layout = (GridLayoutManager)container.getLayout();
          final ArrayList<ActiveSpot> spots = new ArrayList<ActiveSpot>();
          myHorizontalSpots.put(container, spots);
          for(int i = layout.getColumnCount() -1; i >= 0; i--){
            createSpot(spots);
          }

          return true;
        }
      }
    );

    // Now we are ready to layout all horizontal items
    for(Iterator<Map.Entry<RadContainer, ArrayList<ActiveSpot>>> i = myHorizontalSpots.entrySet().iterator(); i.hasNext();){
      final Map.Entry<RadContainer, ArrayList<ActiveSpot>> entry = i.next();
      final RadContainer container = entry.getKey();
      LOG.assertTrue(container.isGrid());
      final GridLayoutManager layout = (GridLayoutManager)container.getLayout();
      final ArrayList<ActiveSpot> spots = entry.getValue();
      LOG.assertTrue(spots.size() == layout.getColumnCount());
      final int[] widths = layout.getWidths();
      final int[] xs = layout.getXs();
      final JComponent delegee = container.getDelegee();
      final Point topLeftPoint = SwingUtilities.convertPoint(delegee, 0, 0, ActiveDecorationLayer.this);
      for(int j = widths.length - 1; j >= 0; j--){
        final ActiveSpot spot = spots.get(j);

        spot.setContainer(container);
        spot.setCell(j);
        spot.setOrientation(SwingConstants.HORIZONTAL);
        spot.setCellSize(widths[j]);
        spot.updateActions();

        final Dimension prefSize = spot.getPreferredSize();
        final int height = prefSize.height;
        final int shift = Math.max(0, widths[j] - prefSize.width)/2;
        spot.setBounds(
          topLeftPoint.x + xs[j] + shift,
          topLeftPoint.y - height,
          widths[j] - shift,
          height
        );
        spot.validate();
      }
    }
  }

  public void paint(final Graphics g){
    // Active spots
    layoutHorizontalSpots();
    layoutVerticalSpots();
    LOG.assertTrue(myVerticalSpots.size() == myHorizontalSpots.size());

    // Paint active decorators
    paintChildren(g);
  }
}