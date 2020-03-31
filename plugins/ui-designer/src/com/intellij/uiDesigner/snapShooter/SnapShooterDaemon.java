// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.snapShooter;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NonNls;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * @author yole
 */
public class SnapShooterDaemon implements Runnable {
  private final Map<Integer, Component> myIdMap = new HashMap<>();
  private int myNextId = 1;
  private final BlockingQueue<String> myCommandQueue = new ArrayBlockingQueue<>(20);
  private final BlockingQueue<String> myResponseQueue = new ArrayBlockingQueue<>(20);
  private final int myPort;

  public SnapShooterDaemon(final int port) {
    myPort = port;
  }

  @Override
  public void run() {
    ServerSocket serverSocket;
    try {
      serverSocket = new ServerSocket(myPort, 50, InetAddress.getLocalHost());
    }
    catch(IOException ex) {
      System.out.println("Failed to open server socket: " + ex);
      return;
    }

    System.out.println("SnapShooter listening on port " + myPort);

    //noinspection InfiniteLoopStatement
    while (true) {
      processClientConnection(serverSocket);
    }
  }

  private void processClientConnection(final ServerSocket serverSocket) {
    Socket clientSocket;
    try {
      clientSocket = serverSocket.accept();
      System.out.println("SnapShooter connection accepted");
      InputStreamReader reader = new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8);
      BufferedReader bufferedReader = new BufferedReader(reader);
      OutputStreamWriter writer = new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8);
      while(true) {
        String command;
        try {
          command = bufferedReader.readLine();
        }
        catch(IOException ex) {
          break;
        }
        if (command == null) {
          System.out.println("End of stream receiving command");
          break;
        }
        processCommand(command, writer);
      }
    }
    catch(IOException ex) {
      System.out.println("Exception in SnapShooter connection: " + ex);
    }
  }

  private void processCommand(@NonNls final String command, final OutputStreamWriter writer) throws IOException {
    if (command.startsWith("S")) {
      SwingUtilities.invokeLater(new SuspendSwingRunnable());
    }
    else {
      myCommandQueue.add(command);
      if (command.startsWith("L") || command.startsWith("X")) {
        String response;
        try {
          response = myResponseQueue.take();
        }
        catch (InterruptedException e) {
          writer.close();
          return;
        }
        writer.write(response);
        writer.flush();
      }
    }
  }

  private String[] getChildren(final int id) {
    List<String> result = new ArrayList<>();
    List<Component> children = getChildList(id);
    for(Component child: children) {
      SnapShotRemoteComponent rc = new SnapShotRemoteComponent(assignId(child),
                                                               child.getClass().getName(),
                                                               getLayoutManagerClass(child),
                                                               getChildText(child));
      result.add(rc.toProtocolString());
    }
    return ArrayUtilRt.toStringArray(result);
  }

  private static String getLayoutManagerClass(final Component component) {
    if (component instanceof JPanel) {
      LayoutManager layoutManager = ((Container) component).getLayout();
      if (layoutManager != null) {
        Class layoutManagerClass = layoutManager.getClass();
        while(!layoutManagerClass.getSuperclass().equals(Object.class)) {
          layoutManagerClass = layoutManagerClass.getSuperclass();
        }
        return layoutManagerClass.getName();
      }
    }
    return "";
  }

  private List<Component> getChildList(final int id) {
    List<Component> children = new ArrayList<>();
    if (id == 0) {
      children = getRootWindows();
    }
    else {
      Component parent = myIdMap.get(id);
      if (parent instanceof RootPaneContainer) {
        RootPaneContainer rpc = (RootPaneContainer) parent;
        children.add(rpc.getContentPane());
      }
      else if (parent instanceof JSplitPane) {
        JSplitPane splitPane = (JSplitPane) parent;
        if (splitPane.getLeftComponent() != null) {
          children.add(splitPane.getLeftComponent());
        }
        if (splitPane.getRightComponent() != null) {
          children.add(splitPane.getRightComponent());
        }
      }
      else if (parent instanceof JScrollPane) {
        JScrollPane scrollPane = (JScrollPane) parent;
        children.add(scrollPane.getViewport().getView());
      }
      else if (parent instanceof Container) {
        Collections.addAll(children, ((Container) parent).getComponents());
      }
    }
    return children;
  }

  private static String getChildText(final Component component) {
    if (component instanceof Frame) {
      Frame frame = (Frame) component;
      return frame.getTitle();
    }

    final AccessibleContext accessibleContext = component.getAccessibleContext();
    if (accessibleContext != null) {
      final String text = accessibleContext.getAccessibleName();
      if (text != null && text.length() > 0) {
        return text;
      }
    }

    return "";
  }

  private int assignId(final Component child) {
    int result = myNextId;
    myIdMap.put(result, child);
    myNextId++;
    return result;
  }

  private static List<Component> getRootWindows() {
    List<Component> result = new ArrayList<>();
    for(Frame frame: Frame.getFrames()) {
      //noinspection HardCodedStringLiteral
      if (!frame.getClass().getName().endsWith("SwingUtilities$SharedOwnerFrame")) {
        result.add(frame);
      }
      for(Window window: frame.getOwnedWindows()) {
        if (window.isVisible()) {
          result.add(window);
        }
      }
    }
    return result;
  }

  private class SuspendSwingRunnable implements Runnable {
    @Override
    public void run() {
      while(true) {
        String command;
        try {
          command = myCommandQueue.take();
        }
        catch (InterruptedException e) {
          break;
        }
        if (command.startsWith("R")) {
          break;
        }
        String response = "";
        if (command.startsWith("L")) {
          response = doListCommand(command);
        }
        else if (command.startsWith("X")) {
          response = doSnapshotCommand(command);
        }
        if (response.length() > 0) {
          System.out.println("Sending response: " + response);
          try {
            myResponseQueue.put(response);
          }
          catch (InterruptedException e) {
            break;
          }
        }
      }
    }

    @NonNls
    private String doSnapshotCommand(final String command) {
      int id = Integer.parseInt(command.substring(1));
      Component component = myIdMap.get(id);
      XmlWriter xmlWriter = new XmlWriter();
      RadRootContainer rootContainer = null;
      try {
        rootContainer = createFormSnapshot((JComponent) component);
      }
      catch (Exception ex) {
        ex.printStackTrace();
        return "E:" + ex.getMessage() + "\n";
      }
      rootContainer.write(xmlWriter);
      return xmlWriter.getText();
    }

    private RadRootContainer createFormSnapshot(final JComponent component) {
      SnapshotContext context = new SnapshotContext();
      final RadComponent radComponent = RadComponent.createSnapshotComponent(context, component);
      if (radComponent != null) {
        radComponent.setBounds(new Rectangle(new Point(10, 10), component.getPreferredSize()));
        context.getRootContainer().addComponent(radComponent);
        context.postProcess();
      }
      return context.getRootContainer();
    }

    @NonNls
    private String doListCommand(final String command) {
      int id = Integer.parseInt(command.substring(1));
      String[] children = getChildren(id);
      StringBuilder result = new StringBuilder();
      for(String child: children) {
        result.append(child).append("\n");
      }
      result.append(".\n");
      return result.toString();
    }
  }
}
