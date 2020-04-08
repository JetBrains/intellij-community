// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.ide.structureView.StructureViewModel;

public interface PropertiesGroupingStructureViewModel extends StructureViewModel.ElementInfoProvider {
  void setSeparator(String separator);

  String getSeparator();

  void setGroupingActive(boolean state);
}
