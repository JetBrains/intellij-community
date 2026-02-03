// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.designSurface;

import java.awt.Component;
import java.awt.Rectangle;


public interface FeedbackLayer {
  void putFeedback(Component relativeTo, final Rectangle rc, final String tooltipText);
  void putFeedback(Component relativeTo, Rectangle rc, final FeedbackPainter feedbackPainter, final String tooltipText);
  void removeFeedback();
}
