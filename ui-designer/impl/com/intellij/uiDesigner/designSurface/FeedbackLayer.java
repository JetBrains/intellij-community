/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author yole
 */
public interface FeedbackLayer {
  void putFeedback(Component relativeTo, final Rectangle rc);
  void putFeedback(Component relativeTo, Rectangle rc, final FeedbackPainter feedbackPainter);
  void putToolTip(Component relativeTo, Point pnt, @Nullable String text);
  void removeFeedback();
}
