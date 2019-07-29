// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.tasks.trello;

import com.intellij.tasks.trello.model.TrelloLabel;
import com.intellij.util.ui.ImageUtil;
import icons.TasksCoreIcons;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TrelloIconBuilder {
  private final Map<Set<TrelloLabel.LabelColor>, Image> CACHE = new HashMap<>();
  public static final double SQRT_2 = Math.sqrt(2.0);

  private final int size;

  public TrelloIconBuilder(int size) {
    this.size = size;
  }

  public Icon buildIcon(Set<TrelloLabel.LabelColor> colorSet) {
    if (colorSet.isEmpty()) {
      return TasksCoreIcons.Trello;
    }
    Image image = CACHE.get(colorSet);
    if (image == null) {
      BufferedImage bufferedImage = ImageUtil.createImage(size, size, BufferedImage.TYPE_INT_ARGB);
      int adjustedSize = size - 1;
      int nStripes = colorSet.size();
      Graphics2D g2d = bufferedImage.createGraphics();
      double diag = adjustedSize * SQRT_2;
      double stripeWidth = diag / nStripes;
      RoundRectangle2D baseRectangle = new RoundRectangle2D.Double(0, 0, adjustedSize, adjustedSize, 2, 2);
      ArrayList<TrelloLabel.LabelColor> colorsList = new ArrayList<>(colorSet);
      for (int i = 0; i < nStripes; i++) {
        Color color = colorsList.get(i).getColor();
        Area stripe = new Area(new Rectangle2D.Double(-diag / 2, (i * stripeWidth), diag, stripeWidth));
        stripe.transform(AffineTransform.getRotateInstance(-Math.PI / 4, 0, 0));
        stripe.intersect(new Area(baseRectangle));
        g2d.setPaint(color);
        g2d.fill(stripe);
      }
      g2d.setPaint(Color.BLACK);
      g2d.draw(baseRectangle);
      image = bufferedImage;
      CACHE.put(colorSet, image);
    }
    return new ImageIcon(image);
  }
}
