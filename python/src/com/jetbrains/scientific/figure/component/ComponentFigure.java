// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.scientific.figure.component;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.scientific.figure.base.FigureBase;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;

import static com.jetbrains.scientific.figure.FigureConstants.THUMBNAIL_ICON_HEIGHT;
import static com.jetbrains.scientific.figure.FigureConstants.THUMBNAIL_ICON_WIDTH;
import static com.jetbrains.scientific.figure.base.FigureUtil.componentImage;
import static com.jetbrains.scientific.figure.base.FigureUtil.fit;

public class ComponentFigure extends FigureBase {
  private final ComponentProvider myComponentProvider;

  public ComponentFigure(ComponentProvider componentProvider) {
    myComponentProvider = componentProvider;
  }

  @Override
  public TabInfo getTabInfo() {
    MyPanel component = new MyPanel();

    TabInfo info = new TabInfo(component);
    info.setTabColor(UIUtil.getPanelBackground());
    info.setText(" ");

    component.setThumbnailIconConsumer(info::setIcon);
    return info;
  }

  private class MyPanel extends JPanel implements Disposable {
    private Dimension myLastSize = new Dimension(0, 0);
    private Runnable myWaitingRunnable = null;
    private Consumer<? super ImageIcon> myThumbnailIconConsumer;

    private MyPanel() {
      super(new FlowLayout(FlowLayout.CENTER, 0, 0));
      setBackground(Color.WHITE);
      addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          Dimension size = MyPanel.this.getSize();
          Runnable runnable = () -> {
            myWaitingRunnable = null;
            if (size.equals(myLastSize)) return;
            myLastSize = size;
            rebuildProvidedComponent(size);
          };

          ApplicationManager.getApplication().runWriteAction(() -> {
            myWaitingRunnable = runnable;
          });
          ApplicationManager.getApplication().invokeLater(
            runnable,
            o -> myWaitingRunnable != runnable
          );
        }
      });
      addContainerListener(new ContainerAdapter() {
        @Override
        public void componentRemoved(ContainerEvent e) {
          Component child = e.getChild();
          if (child instanceof Disposable) {
            Disposer.dispose(((Disposable)child));
          }
        }
      });
    }

    private void setThumbnailIconConsumer(Consumer<? super ImageIcon> consumer) {
      myThumbnailIconConsumer = consumer;
    }

    private void rebuildProvidedComponent(Dimension size) {
      removeAll();

      JComponent providedComponent = myComponentProvider.createComponent(size.width, size.height);

      if (myThumbnailIconConsumer != null) {
        providedComponent.addComponentListener(new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            providedComponent.removeComponentListener(this);
            ApplicationManager.getApplication().invokeLater(() -> {
              Image image = componentImage(MyPanel.this);
              ImageIcon icon = new ImageIcon(fit(image, THUMBNAIL_ICON_WIDTH, THUMBNAIL_ICON_HEIGHT));
              myThumbnailIconConsumer.consume(icon);
            });
          }
        });
      }

      add(providedComponent);

      // Fix tabbed pane issue: after resizing and switching to ComponentFigure tab its panel is rebuilt but not repainted.
      revalidate();
    }

    @Override
    public void dispose() {
      myThumbnailIconConsumer = null;
      removeAll();
    }
  }
}
