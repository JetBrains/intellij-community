/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.designer.designSurface;

import org.jetbrains.annotations.NotNull;

public interface ZoomProvider {
  /**
   * Returns true if zooming is supported by this designer.
   */
  boolean isZoomSupported();

  /**
   * Zoom the editable area.
   */
  void zoom(@NotNull ZoomType type);

  /**
   * Sets the zoom level. Note that this should be 1, not 100 (percent), for an image at its actual size.
   */
  void setZoom(double zoom);

  /**
   * Returns the current zoom level. Note that this is 1, not 100 (percent) for an image at its actual size.
   */
  double getZoom();
}