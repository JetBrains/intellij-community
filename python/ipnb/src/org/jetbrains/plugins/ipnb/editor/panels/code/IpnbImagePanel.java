package org.jetbrains.plugins.ipnb.editor.panels.code;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbImageOutputCell;
import sun.misc.BASE64Decoder;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

public class IpnbImagePanel extends IpnbCodeOutputPanel<IpnbImageOutputCell> {
  private static final Logger LOG = Logger.getInstance(IpnbImagePanel.class);

  public IpnbImagePanel(@NotNull final IpnbImageOutputCell cell, @Nullable IpnbCodePanel ipnbCodePanel) {
    super(cell, null, ipnbCodePanel);
  }

  @Override
  protected JComponent createViewPanel() {
    final String png = myCell.getBase64String();

    final JBLabel label = new ResizableIconLabel();
    if (!StringUtil.isEmptyOrSpaces(png)) {
      try {
        byte[] btDataFile = new BASE64Decoder().decodeBuffer(png);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(btDataFile));
        label.setIcon(new ImageIcon(image));
      }
      catch (Exception e) {
        LOG.error("Couldn't parse image. " + e.getMessage());
      }
    }

    label.setBackground(IpnbEditorUtil.getBackground());
    label.setOpaque(true);

    return label;
  }

  static class ResizableIconLabel extends JBLabel {
    @Override
    public void paintComponent(Graphics g) {
      final Icon icon = getIcon();
      if (icon instanceof ImageIcon) {
        g.drawImage(((ImageIcon)icon).getImage(), 0, 0, getWidth(), getHeight(), this);
      }
      else {
        super.paintComponent(g);
      }
    }
  }
}
