package com.intellij.util.ui;

import javax.swing.*;
import java.awt.*;


public class IdeaUIManager {

  public static Color getTableSelectionBackgroung(){
    return UIManager.getColor("Table.selectionBackground");
  }

  public static Color getTableBackgroung(){
    return UIManager.getColor("Table.Backgroung");
  }

  public static Color getTableSelectionForegroung(){
    return UIManager.getColor("Table.selectionForeground");
  }

  public static Color getTableForegroung(){
    return UIManager.getColor("Table.Foregroung");
  }

  public static Color getTreeForegroung() {
    return UIManager.getColor("Tree.Foregroung");
  }

}
