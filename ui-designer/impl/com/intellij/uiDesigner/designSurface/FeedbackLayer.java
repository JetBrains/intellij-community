/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import java.awt.Component;
import java.awt.Rectangle;

/**
 * @author yole
 */
public interface FeedbackLayer {
  void putFeedback(Component relativeTo, final Rectangle rc, final String tooltipText);
  void putFeedback(Component relativeTo, Rectangle rc, final FeedbackPainter feedbackPainter, final String tooltipText);
  void removeFeedback();
}
