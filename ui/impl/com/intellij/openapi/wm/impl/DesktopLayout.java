package com.intellij.openapi.wm.impl;

import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;

/**
 * @author Vladimir Kondratyev
 */
public final class DesktopLayout implements JDOMExternalizable {
  @NonNls static final String TAG = "layout";
  /**
   * Map between <code>id</code>s and registered <code>WindowInfo</code>s.
   */
  private final com.intellij.util.containers.HashMap<String, WindowInfoImpl> myRegisteredId2Info;
  /**
   * Map between <code>id</code>s and unregistered <code>WindowInfo</code>s.
   */
  private final com.intellij.util.containers.HashMap<String, WindowInfoImpl> myUnregisteredId2Info;
  /**
   *
   */
  private static final MyWindowInfoComparator ourWindowInfoComparator = new MyWindowInfoComparator();
  /**
   * Don't use this member directly. Get it only by <code>getInfos</code> method.
   * It exists here only for optimization purposes. This member can be <code>null</code>
   * if the cached data is invalid.
   */
  private WindowInfoImpl[] myRegisteredInfos;
  /**
   * Don't use this member directly. Get it only by <code>getUnregisteredInfos</code> method.
   * It exists here only for optimization purposes. This member can be <code>null</code>
   * if the cached data is invalid.
   */
  private WindowInfoImpl[] myUnregisteredInfos;
  /**
   * Don't use this member directly. Get it only by <code>getAllInfos</code> method.
   * It exists here only for optimization purposes. This member can be <code>null</code>
   * if the cached data is invalid.
   */
  private WindowInfoImpl[] myAllInfos;
  @NonNls public static final String ID_ATTR = "id";

  public DesktopLayout() {
    myRegisteredId2Info = new com.intellij.util.containers.HashMap<String, WindowInfoImpl>();
    myUnregisteredId2Info = new com.intellij.util.containers.HashMap<String, WindowInfoImpl>();
  }

  /**
   * Copies itself from the passed
   *
   * @param layout to be copied.
   */
  public final void copyFrom(final DesktopLayout layout) {
    final WindowInfoImpl[] infos = layout.getAllInfos();
    for (int i = 0; i < infos.length; i++) {
      WindowInfoImpl info = myRegisteredId2Info.get(infos[i].getId());
      if (info != null) {
        info.copyFrom(infos[i]);
        continue;
      }
      info = myUnregisteredId2Info.get(infos[i].getId());
      if (info != null) {
        info.copyFrom(infos[i]);
      }
      else {
        myUnregisteredId2Info.put(infos[i].getId(), infos[i].copy());
      }
    }
    // invalidate caches
    myRegisteredInfos = null;
    myUnregisteredInfos = null;
    myAllInfos = null;
    // normalize orders
    normalizeOrder(getAllInfos(ToolWindowAnchor.TOP));
    normalizeOrder(getAllInfos(ToolWindowAnchor.LEFT));
    normalizeOrder(getAllInfos(ToolWindowAnchor.BOTTOM));
    normalizeOrder(getAllInfos(ToolWindowAnchor.RIGHT));
  }

  /**
   * Creates or gets <code>WindowInfo</code> for the specified <code>id</code>. If tool
   * window is being registered first time the method uses <code>anchor</code>.
   *
   * @param id     <code>id</code> of tool window to be registered.
   * @param anchor the default tool window anchor.
   * @return
   */
  final WindowInfoImpl register(final String id, final ToolWindowAnchor anchor) {
    WindowInfoImpl info = myUnregisteredId2Info.get(id);
    if (info != null) { // tool window has been already registered some time
      myUnregisteredId2Info.remove(id);
    }
    else { // tool window is being registered first time
      info = new WindowInfoImpl(id);
      info.setAnchor(anchor);
    }
    myRegisteredId2Info.put(id, info);
    // invalidate caches
    myRegisteredInfos = null;
    myUnregisteredInfos = null;
    myAllInfos = null;
    //
    return info;
  }

  final void unregister(final String id) {
    final WindowInfoImpl info = myRegisteredId2Info.remove(id).copy();
    myUnregisteredId2Info.put(id, info);
    // invalidate caches
    myRegisteredInfos = null;
    myUnregisteredInfos = null;
    myAllInfos = null;
  }

  /**
   * @return <code>WindowInfo</code> for the window with specified <code>id</code>.
   *         If <code>onlyRegistered</code> is <code>true</code> then returns not <code>null</code>
   *         value if and only if window with <code>id</code> is registered one.
   */
  final WindowInfoImpl getInfo(final String id, final boolean onlyRegistered) {
    final WindowInfoImpl info = myRegisteredId2Info.get(id);
    if (onlyRegistered || info != null) {
      return info;
    }
    else {
      return myUnregisteredId2Info.get(id);
    }
  }

  final String getActiveId() {
    final WindowInfoImpl[] infos = getInfos();
    for (int i = 0; i < infos.length; i++) {
      if (infos[i].isActive()) {
        return infos[i].getId();
      }
    }
    return null;
  }

  /**
   * @return <code>WindowInfo</code>s for all registered tool windows.
   */
  final WindowInfoImpl[] getInfos() {
    if (myRegisteredInfos == null) {
      myRegisteredInfos = myRegisteredId2Info.values().toArray(new WindowInfoImpl[myRegisteredId2Info.size()]);
    }
    return myRegisteredInfos;
  }

  /**
   * @return <code>WindowInfos</code>s for all windows that are currently unregistered.
   */
  private WindowInfoImpl[] getUnregisteredInfos() {
    if (myUnregisteredInfos == null) {
      myUnregisteredInfos = myUnregisteredId2Info.values().toArray(new WindowInfoImpl[myUnregisteredId2Info.size()]);
    }
    return myUnregisteredInfos;
  }

  /**
   * @return <code>WindowInfo</code>s of all (registered and unregistered) tool windows.
   */
  private WindowInfoImpl[] getAllInfos() {
    final WindowInfoImpl[] registeredInfos = getInfos();
    final WindowInfoImpl[] unregisteredInfos = getUnregisteredInfos();
    myAllInfos = ArrayUtil.mergeArrays(registeredInfos, unregisteredInfos, WindowInfoImpl.class);
    return myAllInfos;
  }

  /**
   * @return all (registered and not unregistered) <code>WindowInfos</code> for the specified <code>anchor</code>.
   *         Returned infos are sorted by order.
   */
  private WindowInfoImpl[] getAllInfos(final ToolWindowAnchor anchor) {
    WindowInfoImpl[] infos = getAllInfos();
    final ArrayList<WindowInfoImpl> list = new ArrayList<WindowInfoImpl>(infos.length);
    for (int i = 0; i < infos.length; i++) {
      if (anchor == infos[i].getAnchor()) {
        list.add(infos[i]);
      }
    }
    infos = list.toArray(new WindowInfoImpl[list.size()]);
    Arrays.sort(infos, ourWindowInfoComparator);
    return infos;
  }

  /**
   * Normalizes order of windows in the passed array. Note, that array should be
   * sorted by order (by ascending). Order of first window will be <code>0</code>.
   */
  private static void normalizeOrder(final WindowInfoImpl[] infos) {
    for (int i = 0; i < infos.length; i++) {
      infos[i].setOrder(i);
    }
  }

  final boolean isToolWindowRegistered(final String id) {
    return myRegisteredId2Info.containsKey(id);
  }

  /**
   * @return comparator which compares <code>StripeButtons</code> in the stripe with
   *         specified <code>anchor</code>.
   */
  final Comparator comparator(final ToolWindowAnchor anchor) {
    return new MyStripeButtonComparator(anchor);
  }

  /**
   * @param anchor anchor of the stripe.
   * @return maximum ordinal number in the specified stripe. Returns <code>-1</code>
   *         if there is no any tool window with the specified anchor.
   */
  private int getMaxOrder(final ToolWindowAnchor anchor) {
    int res = -1;
    final WindowInfoImpl[] infos = getAllInfos();
    for (int i = 0; i < infos.length; i++) {
      final WindowInfoImpl info = infos[i];
      if (anchor == info.getAnchor() && res < info.getOrder()) {
        res = info.getOrder();
      }
    }
    return res;
  }

  /**
   * Sets new <code>anchor</code> and <code>id</code> for the specified tool window.
   * Also the method properly updates order of all other tool windows.
   *
   * @param newAnchor new anchor
   * @param newOrder  new order
   */
  final void setAnchor(final String id, final ToolWindowAnchor newAnchor, int newOrder) {
    if (newOrder == -1) { // if order isn't defined then the window will the last in the stripe
      newOrder = getMaxOrder(newAnchor) + 1;
    }
    final WindowInfoImpl info = getInfo(id, true);
    final ToolWindowAnchor oldAnchor = info.getAnchor();
    // Shift order to the right in the target stripe.
    final WindowInfoImpl[] infos = getAllInfos(newAnchor);
    for (int i = infos.length - 1; i > -1; i--) {
      final WindowInfoImpl info2 = infos[i];
      if (newOrder <= info2.getOrder()) {
        info2.setOrder(info2.getOrder() + 1);
      }
    }
    // "move" window into the target position
    info.setAnchor(newAnchor);
    info.setOrder(newOrder);
    // Normalize orders in the source and target stripes
    normalizeOrder(getAllInfos(oldAnchor));
    if (oldAnchor != newAnchor) {
      normalizeOrder(getAllInfos(newAnchor));
    }
  }

  public final void readExternal(final org.jdom.Element layoutElement) {
    for (Iterator i = layoutElement.getChildren().iterator(); i.hasNext();) {
      final Element e = (Element)i.next();
      if (WindowInfoImpl.TAG.equals(e.getName())) {
        final WindowInfoImpl info = new WindowInfoImpl(e.getAttributeValue(ID_ATTR));
        info.readExternal(e);
        if (info.getOrder() == -1) { // if order isn't defined then window's button will be the last one in the stripe
          info.setOrder(getMaxOrder(info.getAnchor()) + 1);
        }
        myUnregisteredId2Info.put(info.getId(), info);
      }
    }
  }

  public final void writeExternal(final Element layoutElement) {
    final WindowInfoImpl[] infos = getAllInfos();
    for (int i = 0; i < infos.length; i++) {
      final Element element = new Element(WindowInfoImpl.TAG);
      infos[i].writeExternal(element);
      layoutElement.addContent(element);
    }
  }

  private static final class MyWindowInfoComparator implements Comparator {
    public int compare(final Object obj1, final Object obj2) {
      final WindowInfoImpl info1 = (WindowInfoImpl)obj1;
      final WindowInfoImpl info2 = (WindowInfoImpl)obj2;
      return info1.getOrder() - info2.getOrder();
    }
  }

  private final class MyStripeButtonComparator implements Comparator {
    private final com.intellij.util.containers.HashMap<String, WindowInfoImpl> myId2Info;

    public MyStripeButtonComparator(final ToolWindowAnchor anchor) {
      myId2Info = new com.intellij.util.containers.HashMap<String, WindowInfoImpl>();
      final WindowInfoImpl[] infos = getInfos();
      for (int i = 0; i < infos.length; i++) {
        final WindowInfoImpl info = infos[i];
        if (anchor == info.getAnchor()) {
          myId2Info.put(info.getId(), info.copy());
        }
      }
    }

    public final int compare(final Object obj1, final Object obj2) {
      final WindowInfoImpl info1 = myId2Info.get(((StripeButton)obj1).getWindowInfo().getId());
      final int order1 = info1 != null ? info1.getOrder() : 0;

      final WindowInfoImpl info2 = myId2Info.get(((StripeButton)obj2).getWindowInfo().getId());
      final int order2 = info2 != null ? info2.getOrder() : 0;

      return order1 - order2;
    }
  }
}