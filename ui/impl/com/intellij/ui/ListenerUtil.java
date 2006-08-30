package com.intellij.ui;

import java.awt.*;
import java.awt.event.FocusListener;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public class ListenerUtil {
  public static void addFocusListener(Component component, FocusListener l) {
    component.addFocusListener(l);
    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        addFocusListener(container.getComponent(i), l);
      }
    }
  }

  public static void removeFocusListener(Component component, FocusListener l) {
    component.removeFocusListener(l);
    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        removeFocusListener(container.getComponent(i), l);
      }
    }
  }

  public static void addMouseListener(Component component, MouseListener l) {
    component.addMouseListener(l);
    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        addMouseListener(container.getComponent(i), l);
      }
    }
  }

  public static void addMouseMotionListener(Component c, MouseMotionListener l) {
    c.addMouseMotionListener(l);
    if (c instanceof Container) {
      final Container container = (Container)c;
      Component[] children = container.getComponents();
      for (Component child : children) {
        addMouseMotionListener(child, l);
      }
    }
  }

  public static void removeMouseListener(Component component, MouseListener l) {
    component.removeMouseListener(l);
    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        removeMouseListener(container.getComponent(i), l);
      }
    }
  }

  public static void addKeyListener(Component component, KeyListener l) {
    component.addKeyListener(l);
    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        addKeyListener(container.getComponent(i), l);
      }
    }
  }

  public static void removeKeyListener(Component component, KeyListener l) {
    component.removeKeyListener(l);
    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        removeKeyListener(container.getComponent(i), l);
      }
    }
  }
}