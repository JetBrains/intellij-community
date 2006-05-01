package com.intellij.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.util.IJSwingUtilities;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.TabbedPaneUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class TabbedPaneWrapper {
  protected final TabbedPane myTabbedPane;
  protected final JComponent myTabbedPaneHolder;

  public TabbedPaneWrapper(){
    this(SwingConstants.TOP);
  }

  /**
   * Creates tabbed pane wrapper with specified tab placement
   *
   * @param tabPlacement tab placement. It one of the <code>SwingConstants.TOP</code>,
   * <code>SwingConstants.LEFT</code>, <code>SwingConstants.BOTTOM</code> or
   * <code>SwingConstants.RIGHT</code>.
   */
  public TabbedPaneWrapper(final int tabPlacement) {
    this(tabPlacement, true);
  }


  public TabbedPaneWrapper(int tabPlacement, boolean installKeyboardNavigation) {
    assertIsDispatchThread();

    myTabbedPane = createTabbedPane(tabPlacement);
    myTabbedPane.myInstallKeyboardNavigation = installKeyboardNavigation;
    myTabbedPaneHolder = createTabbedPaneHolder();
    myTabbedPaneHolder.add(myTabbedPane, BorderLayout.CENTER);
    myTabbedPaneHolder.setFocusCycleRoot(true);
    myTabbedPaneHolder.setFocusTraversalPolicy(new _MyFocusTraversalPolicy());
  }

  private static void assertIsDispatchThread() {
    final Application application = ApplicationManager.getApplication();
    if (application != null){
      application.assertIsDispatchThread();
    }
  }

  public final void addChangeListener(final ChangeListener listener){
    assertIsDispatchThread();
    myTabbedPane.addChangeListener(listener);
  }

  public final void removeChangeListener(final ChangeListener listener){
    assertIsDispatchThread();
    myTabbedPane.removeChangeListener(listener);
  }

  protected TabbedPaneHolder createTabbedPaneHolder() {
    return new TabbedPaneHolder();
  }

  public final JComponent getComponent() {
    assertIsDispatchThread();
    return myTabbedPaneHolder;
  }

  /**
   * @see javax.swing.JTabbedPane#addTab(java.lang.String, javax.swing.Icon, java.awt.Component, java.lang.String)
   */
  public final synchronized void addTab(final String title, final Icon icon, final JComponent component, final String tip) {
    insertTab(title, icon, component, tip, myTabbedPane.getTabCount());
  }

  public final synchronized void addTab(final String title, final JComponent component) {
    insertTab(title, null, component, null, myTabbedPane.getTabCount());
  }

  public synchronized void insertTab(final String title, final Icon icon, final JComponent component, final String tip, final int index) {
    myTabbedPane.insertTab(title, icon, new TabWrapper(component), tip, index);
  }

  protected TabbedPane createTabbedPane(final int tabPlacement) {
    return new TabbedPane(tabPlacement);
  }

  /**
   * @see javax.swing.JTabbedPane#setTabPlacement
   */
  public final void setTabPlacement(final int tabPlacement) {
    assertIsDispatchThread();
    myTabbedPane.setTabPlacement(tabPlacement);
  }

  public final void addMouseListener(final MouseListener listener) {
    assertIsDispatchThread();
    myTabbedPane.addMouseListener(listener);
  }

  public final synchronized int getSelectedIndex() {
    return myTabbedPane.getSelectedIndex();
  }

  /**
   * @see javax.swing.JTabbedPane#getSelectedComponent()
   */
  public final synchronized JComponent getSelectedComponent() {
    final TabWrapper tabWrapper = (TabWrapper)myTabbedPane.getSelectedComponent();
    return tabWrapper != null ? tabWrapper.getComponent() : null;
  }

  public final void setSelectedIndex(final int index) {
    assertIsDispatchThread();

    final boolean hadFocus = IJSwingUtilities.hasFocus2(myTabbedPaneHolder);
    myTabbedPane.setSelectedIndex(index);
    if (hadFocus) {
      myTabbedPaneHolder.requestFocus();
    }
  }

  public final void setSelectedComponent(final JComponent component){
    assertIsDispatchThread();

    final int index=indexOfComponent(component);
    if(index==-1){
      throw new IllegalArgumentException("component not found in tabbed pane wrapper");
    }
    setSelectedIndex(index);
  }

  public final synchronized void removeTabAt(final int index) {
    assertIsDispatchThread();

    final boolean hadFocus = IJSwingUtilities.hasFocus2(myTabbedPaneHolder);
    myTabbedPane.removeTabAt(index);
    if (myTabbedPane.getTabCount() == 0) {
      // to clear BasicTabbedPaneUI.visibleComponent field
      myTabbedPane.revalidate();
    }
    if (hadFocus) {
      myTabbedPaneHolder.requestFocus();
    }
  }

  public final synchronized int getTabCount() {
    return myTabbedPane.getTabCount();
  }

  public final Color getForegroundAt(final int index){
    assertIsDispatchThread();
    return myTabbedPane.getForegroundAt(index);
  }

  /**
   * @see javax.swing.JTabbedPane#setForegroundAt(int, java.awt.Color)
   */
  public final void setForegroundAt(final int index,final Color color){
    assertIsDispatchThread();
    myTabbedPane.setForegroundAt(index,color);
  }

  /**
   * @see javax.swing.JTabbedPane#setComponentAt(int, java.awt.Component)
   */
  public final synchronized JComponent getComponentAt(final int i) {
    return ((TabWrapper)myTabbedPane.getComponentAt(i)).getComponent();
  }

  public final void setTitleAt(final int index, final String title) {
    assertIsDispatchThread();
    myTabbedPane.setTitleAt(index, title);
  }

  public final void setToolTipTextAt(final int index, final String toolTipText) {
    assertIsDispatchThread();
    myTabbedPane.setToolTipTextAt(index, toolTipText);
  }

  /**
   * @see javax.swing.JTabbedPane#setComponentAt(int, java.awt.Component)
   */
  public final synchronized void setComponentAt(final int index, final JComponent component) {
    assertIsDispatchThread();
    myTabbedPane.setComponentAt(index, new TabWrapper(component));
  }

  /**
   * @see javax.swing.JTabbedPane#setIconAt(int, javax.swing.Icon)
   */
  public final void setIconAt(final int index, final Icon icon) {
    assertIsDispatchThread();
    myTabbedPane.setIconAt(index, icon);
  }

  public final void setEnabledAt(final int index, final boolean enabled) {
    assertIsDispatchThread();
    myTabbedPane.setEnabledAt(index, enabled);
  }

  /**
   * @see javax.swing.JTabbedPane#indexOfComponent(java.awt.Component)
   */
  public final synchronized int indexOfComponent(final JComponent component) {
    for (int i=0; i < myTabbedPane.getTabCount(); i++) {
      final JComponent c = ((TabWrapper)myTabbedPane.getComponentAt(i)).getComponent();
      if (c == component) {
        return i;
      }
    }
    return -1;
  }

  /**
   * @see javax.swing.JTabbedPane#getTabLayoutPolicy
   */
  public final synchronized int getTabLayoutPolicy(){
    return myTabbedPane.getTabLayoutPolicy();
  }

  /**
   * @see javax.swing.JTabbedPane#setTabLayoutPolicy
   */
  public final synchronized void setTabLayoutPolicy(final int policy){
    myTabbedPane.setTabLayoutPolicy(policy);
    final int index=myTabbedPane.getSelectedIndex();
    if(index!=-1){
      myTabbedPane.scrollTabToVisible(index);
    }
  }

  /**
   * @deprecated Keyboard navigation is installed/deinstalled automatically. This method does nothing now.
   */
  public final void installKeyboardNavigation(){
  }

  /**
   * @deprecated Keyboard navigation is installed/deinstalled automatically. This method does nothing now.
   */
  public final void uninstallKeyboardNavigation(){
  }

  public final String getTitleAt(final int i) {
    return myTabbedPane.getTitleAt(i);
  }

  private static final class TabWrapper extends JPanel implements DataProvider{
    private final JComponent myComponent;

    public TabWrapper(final JComponent component) {
      super(new BorderLayout());
      if (component == null) {
        throw new IllegalArgumentException("component cannot be null");
      }
      myComponent = component;
      add(component, BorderLayout.CENTER);
    }

    /*
     * Make possible to search down for DataProviders
     */
    public Object getData(final String dataId) {
      if(myComponent instanceof DataProvider){
        return ((DataProvider)myComponent).getData(dataId);
      } else {
        return null;
      }
    }

    public JComponent getComponent() {
      return myComponent;
    }

    public boolean requestDefaultFocus() {
      final JComponent preferredFocusedComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent(myComponent);
      if (preferredFocusedComponent != null) {
        if (!preferredFocusedComponent.requestFocusInWindow()) {
          preferredFocusedComponent.requestFocus();
        }
        return true;
      } else {
        return myComponent.requestDefaultFocus();
      }
    }

    public void requestFocus() {
      requestDefaultFocus();
    }

    public boolean requestFocusInWindow() {
      return requestDefaultFocus();
    }
  }

  protected static class TabbedPane extends JTabbedPane {
    private ScrollableTabSupport myScrollableTabSupport;
    private AnAction myNextTabAction = null;
    private AnAction myPreviousTabAction = null;
    private boolean myInstallKeyboardNavigation = true;

    public TabbedPane(final int tabPlacement) {
      super(tabPlacement);
      setFocusable(false);
      addMouseListener(
        new MouseAdapter() {
          public void mouseClicked(final MouseEvent e) {
            _requestDefaultFocus();
          }
        }
      );
    }

    @Override
    public void addNotify() {
      super.addNotify();
      if (myInstallKeyboardNavigation) {
        installKeyboardNavigation();
      }
    }

    @Override
    public void removeNotify() {
      super.removeNotify();
      if (myInstallKeyboardNavigation) {
        uninstallKeyboardNavigation();
      }
    }

    @SuppressWarnings({"NonStaticInitializer"})
    private void installKeyboardNavigation(){
      myNextTabAction = new AnAction() {
        {
          setEnabledInModalContext(true);
        }

        public void actionPerformed(final AnActionEvent e) {
          int index = getSelectedIndex() + 1;
          if (index >= getTabCount()) {
            index = 0;
          }
          setSelectedIndex(index);
        }
      };
      myNextTabAction.registerCustomShortcutSet(
        ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_TAB).getShortcutSet(),
        this
      );

      myPreviousTabAction = new AnAction() {
        {
          setEnabledInModalContext(true);
        }

        public void actionPerformed(final AnActionEvent e) {
          int index = getSelectedIndex() - 1;
          if (index < 0) {
            index = getTabCount() - 1;
          }
          setSelectedIndex(index);
        }
      };
      myPreviousTabAction.registerCustomShortcutSet(
        ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_TAB).getShortcutSet(),
        this
      );
    }

    private void uninstallKeyboardNavigation() {
      if (myNextTabAction != null) {
        myNextTabAction.unregisterCustomShortcutSet(this);
        myNextTabAction = null;
      }
      if (myPreviousTabAction != null) {
        myPreviousTabAction.unregisterCustomShortcutSet(this);
        myPreviousTabAction = null;
      }
    }


    public void setUI(final TabbedPaneUI ui){
      super.setUI(ui);
      if(ui instanceof BasicTabbedPaneUI){
        myScrollableTabSupport=new ScrollableTabSupport((BasicTabbedPaneUI)ui);
      }else{
        myScrollableTabSupport=null;
      }
    }

    /**
     * Scrolls tab to visible area. If tabbed pane has <code>JTabbedPane.WRAP_TAB_LAYOUT</code> layout policy then
     * the method does nothing.
     * @param index index of tab to be scrolled.
     */
    public final void scrollTabToVisible(final int index){
      if(myScrollableTabSupport==null||JTabbedPane.WRAP_TAB_LAYOUT==getTabLayoutPolicy()){ // tab scrolling isn't supported by UI
        return;
      }
      final TabbedPaneUI tabbedPaneUI=getUI();
      Rectangle tabBounds=tabbedPaneUI.getTabBounds(this,index);
      final int tabPlacement=getTabPlacement();
      if(SwingConstants.TOP==tabPlacement || SwingConstants.BOTTOM==tabPlacement){ //tabs are on the top or bottom
        if(tabBounds.x<50){  //if tab is to the left of visible area
          int leadingTabIndex=myScrollableTabSupport.getLeadingTabIndex();
          while(leadingTabIndex != index && leadingTabIndex>0 && tabBounds.x<50){
            myScrollableTabSupport.setLeadingTabIndex(leadingTabIndex-1);
            leadingTabIndex=myScrollableTabSupport.getLeadingTabIndex();
            tabBounds=tabbedPaneUI.getTabBounds(this,index);
          }
        }else if(tabBounds.x+tabBounds.width>getWidth()-50){ // if tab's right side is out of visible range
          int leadingTabIndex=myScrollableTabSupport.getLeadingTabIndex();
          while(leadingTabIndex != index && leadingTabIndex<getTabCount()-1 && tabBounds.x+tabBounds.width>getWidth()-50){
            myScrollableTabSupport.setLeadingTabIndex(leadingTabIndex+1);
            leadingTabIndex=myScrollableTabSupport.getLeadingTabIndex();
            tabBounds=tabbedPaneUI.getTabBounds(this,index);
          }
        }
      }else{ // tabs are on left or right side
        if(tabBounds.y<30){ //tab is above visible area
          int leadingTabIndex=myScrollableTabSupport.getLeadingTabIndex();
          while(leadingTabIndex != index && leadingTabIndex>0 && tabBounds.y<30){
            myScrollableTabSupport.setLeadingTabIndex(leadingTabIndex-1);
            leadingTabIndex=myScrollableTabSupport.getLeadingTabIndex();
            tabBounds=tabbedPaneUI.getTabBounds(this,index);
          }
        } else if(tabBounds.y+tabBounds.height>getHeight()-30){  //tab is under visible area
          int leadingTabIndex=myScrollableTabSupport.getLeadingTabIndex();
          while(leadingTabIndex != index && leadingTabIndex<getTabCount()-1 && tabBounds.y+tabBounds.height>getHeight()-30){
            myScrollableTabSupport.setLeadingTabIndex(leadingTabIndex+1);
            leadingTabIndex=myScrollableTabSupport.getLeadingTabIndex();
            tabBounds=tabbedPaneUI.getTabBounds(this,index);
          }
        }
      }
    }

    public void setSelectedIndex(final int index){
      super.setSelectedIndex(index);
      scrollTabToVisible(index);
      doLayout();
    }

    public final void removeTabAt (final int index) {
      super.removeTabAt (index);
      //This event should be fired necessarily because when swing fires an event
      // page to be removed is still in the tabbed pane. There can be a situation when
      // event fired according to swing event contains invalid information about selected page.
      fireStateChanged();
    }

    private void _requestDefaultFocus() {
      final TabWrapper selectedComponent = (TabWrapper)getSelectedComponent();
      if (selectedComponent != null) {
        selectedComponent.requestDefaultFocus();
      }
      else {
        super.requestDefaultFocus();
      }
    }

    protected final int getTabIndexAt(final int x,final int y){
      final TabbedPaneUI ui=getUI();
      for (int i = 0; i < getTabCount(); i++) {
        final Rectangle bounds = ui.getTabBounds(this, i);
        if (bounds.contains(x, y)) {
          return i;
        }
      }
      return -1;
    }

    /**
     * That is hack-helper for working with scrollable tab layout. The problem is BasicTabbedPaneUI doesn't
     * have any API to scroll tab to visible area. Therefore we have to implement it...
     */
    private final class ScrollableTabSupport{
      private final BasicTabbedPaneUI myUI;
      @NonNls public static final String TAB_SCROLLER_NAME = "tabScroller";
      @NonNls public static final String LEADING_TAB_INDEX_NAME = "leadingTabIndex";
      @NonNls public static final String SET_LEADING_TAB_INDEX_METHOD = "setLeadingTabIndex";

      public ScrollableTabSupport(final BasicTabbedPaneUI ui){
        myUI=ui;
      }

      /**
       * @return value of <code>leadingTabIndex</code> field of BasicTabbedPaneUI.ScrollableTabSupport class.
       */
      public int getLeadingTabIndex(){
        try{
          final Field tabScrollerField=BasicTabbedPaneUI.class.getDeclaredField(TAB_SCROLLER_NAME);
          tabScrollerField.setAccessible(true);
          final Object tabScrollerValue=tabScrollerField.get(myUI);

          final Field leadingTabIndexField=tabScrollerValue.getClass().getDeclaredField(LEADING_TAB_INDEX_NAME);
          leadingTabIndexField.setAccessible(true);
          return leadingTabIndexField.getInt(tabScrollerValue);
        }catch(Exception exc){
          final StringWriter writer=new StringWriter();
          exc.printStackTrace(new PrintWriter(writer));
          throw new IllegalStateException("myUI="+myUI+"; cause="+writer.getBuffer());
        }
      }

      public void setLeadingTabIndex(final int leadingIndex){
        try{
          final Field tabScrollerField=BasicTabbedPaneUI.class.getDeclaredField(TAB_SCROLLER_NAME);
          tabScrollerField.setAccessible(true);
          final Object tabScrollerValue=tabScrollerField.get(myUI);

          Method setLeadingIndexMethod=null;
          final Method[] methods=tabScrollerValue.getClass().getDeclaredMethods();
          for (final Method method : methods) {
            if (SET_LEADING_TAB_INDEX_METHOD.equals(method.getName())) {
              setLeadingIndexMethod = method;
              break;
            }
          }
          if(setLeadingIndexMethod==null){
            throw new IllegalStateException("method setLeadingTabIndex not found");
          }
          setLeadingIndexMethod.setAccessible(true);
          setLeadingIndexMethod.invoke(
            tabScrollerValue,
            new Integer(getTabPlacement()), new Integer(leadingIndex));
        }catch(Exception exc){
          final StringWriter writer=new StringWriter();
          exc.printStackTrace(new PrintWriter(writer));
          throw new IllegalStateException("myUI="+myUI+"; cause="+writer.getBuffer());
        }
      }
    }
  }

  private final class _MyFocusTraversalPolicy extends IdeFocusTraversalPolicy{
    public final Component getDefaultComponentImpl(final Container focusCycleRoot) {
      final JComponent component=getSelectedComponent();
      if(component!=null){
        return IdeFocusTraversalPolicy.getPreferredFocusedComponent(component, this);
      }else{
        return null;
      }
    }
  }

  protected class TabbedPaneHolder extends JPanel {
    public TabbedPaneHolder() {
      super(new BorderLayout());
    }

    public final boolean requestDefaultFocus() {
      final JComponent preferredFocusedComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent(myTabbedPane);
      if (preferredFocusedComponent != null) {
        if (!preferredFocusedComponent.requestFocusInWindow()) {
          preferredFocusedComponent.requestFocus();
        }
        return true;
      } else {
        return super.requestDefaultFocus();
      }
    }

    public final void requestFocus() {
      requestDefaultFocus();
    }

    public final boolean requestFocusInWindow() {
      return requestDefaultFocus();
    }
  }

  private static class UnregisterCommand {
    private final AnAction myAction;
    private final JComponent myComponent;

    public UnregisterCommand(AnAction action, JComponent component) {
      myAction = action;
      myComponent = component;
    }

    public void unregister() {
      myAction.unregisterCustomShortcutSet(myComponent);
    }
  }
}
