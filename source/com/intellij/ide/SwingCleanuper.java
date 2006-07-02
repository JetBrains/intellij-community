package com.intellij.ide;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.wm.impl.IdeFrame;
import com.intellij.util.Alarm;

import javax.swing.FocusManager;
import javax.swing.*;
import javax.swing.plaf.basic.BasicPopupMenuUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.dnd.DragGestureRecognizer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * This class listens event from ProjectManager and cleanup some
 * internal Swing references.
 *
 * @author Vladimir Kondratyev
 */
public final class SwingCleanuper implements ApplicationComponent{
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.SwingCleanuper");

  private final Alarm myAlarm;

  /** Invoked by reflection
   * @param projectManager   */
  SwingCleanuper(ProjectManager projectManager){
    myAlarm=new Alarm();

    projectManager.addProjectManagerListener(
      new ProjectManagerAdapter(){
        public void projectOpened(final Project project) {
          myAlarm.cancelAllRequests();
        }
        // Swing keeps references to the last focused component inside DefaultKeyboardFocusManager.realOppositeComponent
        // which is used to compose next focus event. Actually this component could be an editors or a tool window. To fix this
        // memory leak we (if the project was closed and a new one was not opened yet) request focus to the status bar and after
        // the focus events have passed the queue, we put 'null' to the DefaultKeyboardFocusManager.realOppositeComponent field.
        public void projectClosed(final Project project){
          myAlarm.cancelAllRequests();
          myAlarm.addRequest(
            new Runnable() {
              public void run() {
                // request focus into some focusable somponent inside IdeFrame
                final IdeFrame frame;
                final Window window=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
                if(window instanceof IdeFrame){
                  frame=(IdeFrame)window;
                }else{
                  frame=(IdeFrame)SwingUtilities.getAncestorOfClass(IdeFrame.class,window);
                }
                if(frame!=null){
                  ((JComponent)frame.getStatusBar()).requestFocus();
                }

                SwingUtilities.invokeLater(
                  new Runnable() {
                    @SuppressWarnings({"HardCodedStringLiteral"})
                    public void run() {

                      // KeyboardFocusManager.newFocusOwner
                      try{
                        //noinspection HardCodedStringLiteral
                        final Field newFocusOwnerField = KeyboardFocusManager.class.getDeclaredField("newFocusOwner");
                        newFocusOwnerField.setAccessible(true);
                        newFocusOwnerField.set(null, null);
                      }
                      catch(final Exception exc){
                        LOG.error(exc);
                      }

                      // Clear "realOppositeComponent", "realOppositeWindow"

                      final KeyboardFocusManager focusManager = FocusManager.getCurrentKeyboardFocusManager();
                      if (focusManager instanceof DefaultKeyboardFocusManager) {
                        try {
                          final Field realOppositeComponentField = DefaultKeyboardFocusManager.class.getDeclaredField("realOppositeComponent");
                          realOppositeComponentField.setAccessible(true);
                          realOppositeComponentField.set(focusManager, null);

                          final Field realOppositeWindowField = DefaultKeyboardFocusManager.class.getDeclaredField("realOppositeWindow");
                          realOppositeWindowField.setAccessible(true);
                          realOppositeWindowField.set(focusManager, null);
                        } catch(Exception e){
                          LOG.error(e);
                        }
                      }

                      // Memory leak on static field in BasicPopupMenuUI

                      try {
                        final Field menuKeyboardHelperField = BasicPopupMenuUI.class.getDeclaredField("menuKeyboardHelper");
                        menuKeyboardHelperField.setAccessible(true);
                        final Object helperObject = menuKeyboardHelperField.get(null);

                        if (null != helperObject) {
                          final Field lastFocusedField = helperObject.getClass().getDeclaredField("lastFocused");
                          lastFocusedField.setAccessible(true);
                          lastFocusedField.set(helperObject, null);
                        }
                      }
                      catch (Exception e) {
                        LOG.error(e);
                      }

                      // Memory leak on javax.swing.TransferHandler$SwingDragGestureRecognizer.component

                      try{
                        final Field recognizerField = TransferHandler.class.getDeclaredField("recognizer");
                        recognizerField.setAccessible(true);
                        final Object recognizerObject = recognizerField.get(null);
                        if(recognizerObject!=null){ // that is memory leak
                          final Method setComponentMethod = DragGestureRecognizer.class.getDeclaredMethod(
                            "setComponent",
                            new Class[]{Component.class}
                          );
                          setComponentMethod.invoke(recognizerObject,new Object[]{null});
                        }
                      }catch (Exception e){
                        LOG.error(e);
                      }
                      try {
                        fixJTextComponentMemoryLeak();
                      } catch(NoSuchFieldException e) {
                        // JDK 1.5
                      } catch(Exception e) {
                        LOG.error(e);
                      }

                      focusManager.setGlobalCurrentFocusCycleRoot(null); //Remove focus leaks

                      try {
                        final Method m = KeyboardFocusManager.class.getDeclaredMethod("setGlobalFocusOwner", Component.class);
                        m.setAccessible(true);
                        m.invoke(focusManager, new Object[]{null});
                      }
                      catch (Exception e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                      }
                    }
                  }
                );
              }
            },
            2500
          );
        }
      }
    );
  }

  public final void disposeComponent(){}

  public final String getComponentName(){
    return "SwingCleanuper";
  }

  public final void initComponent() { }

  private static void fixJTextComponentMemoryLeak() throws NoSuchFieldException, IllegalAccessException {
    //noinspection HardCodedStringLiteral
    final Field focusedComponentField = JTextComponent.class.getDeclaredField("focusedComponent");
    focusedComponentField.setAccessible(true);
    final JTextComponent component = (JTextComponent)focusedComponentField.get(null);
    if (component != null && !component.isDisplayable()){
      focusedComponentField.set(null, null);
    }
  }

}