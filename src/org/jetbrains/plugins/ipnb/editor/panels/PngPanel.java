package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.output.PngCellOutput;
import sun.misc.BASE64Decoder;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class PngPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance(PngPanel.class);
  @NotNull private final Project myProject;

  public PngPanel(@NotNull final Project project, @NotNull final PngCellOutput cell) {
    super(new BorderLayout());
    myProject = project;
    add(createPanel(cell), BorderLayout.CENTER);
  }

  private JLabel createPanel(@NotNull final PngCellOutput cell) {
    String png = cell.getPng();

    final JBLabel label = new JBLabel();
    try {
      byte[] btDataFile = new BASE64Decoder().decodeBuffer(png);
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(btDataFile));
      label.setIcon(new ImageIcon(image));
    }
    catch (IOException e) {
      LOG.error("Couldn't parse image. " + e.getMessage());
    }

    label.setBackground(IpnbEditorUtil.getBackground());
    label.setOpaque(true);

    return label;
  }

}
