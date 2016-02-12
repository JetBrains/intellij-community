package org.jetbrains.plugins.ipnb.editor.panels.code;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Base64;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbImageOutputCell;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

public class IpnbImagePanel extends IpnbCodeOutputPanel<IpnbImageOutputCell> {
  private static final Logger LOG = Logger.getInstance(IpnbImagePanel.class);

  public IpnbImagePanel(@NotNull final IpnbImageOutputCell cell) {
    super(cell);
  }

  @Override
  protected JComponent createViewPanel() {
    final String png = myCell.getBase64String();

    final JBLabel label = new JBLabel();
    if (!StringUtil.isEmptyOrSpaces(png)) {
      try {
        byte[] btDataFile = Base64.decode(png);
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
}