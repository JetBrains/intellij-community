package com.intellij.openapi.ui.ex;

import com.intellij.openapi.ui.MultiLineLabelUI;

import javax.swing.*;
import java.awt.*;

public class MultiLineLabel extends JLabel{
  public MultiLineLabel(){
  }

  public MultiLineLabel(String text){
    super(text);
  }

  public void updateUI(){
    setUI(new MultiLineLabelUI());
  }

  public Dimension getMinimumSize(){
    return getPreferredSize();
  }
}
