package org.jetbrains.plugins.ipnb.editor.panels;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class IpnbPanel extends JPanel {
  private boolean myEditing;

  public IpnbPanel() {
    super(new BorderLayout());
  }
  public IpnbPanel(@NotNull final LayoutManager layoutManager) {
    super(layoutManager);
  }

  public boolean contains(int y) {
    return y>= getTop() && y<=getBottom();
  }

  public int getTop() {
    return getY();
  }

  public int getBottom() {
    return getTop() + getHeight();
  }

  public boolean isEditing() {
    return myEditing;
  }

  public void setEditing(boolean editing) {
    myEditing = editing;
  }
}
