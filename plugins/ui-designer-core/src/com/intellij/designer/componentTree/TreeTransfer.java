/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.componentTree;

import com.intellij.ide.dnd.FileCopyPasteUtil;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.io.IOException;

/**
 * @author Alexander Lobas
 */
public final class TreeTransfer extends TransferHandler implements Transferable {
  private static final DataFlavor DATA_FLAVOR = FileCopyPasteUtil.createDataFlavor(DataFlavor.javaJVMLocalObjectMimeType, Class.class);

  private final Object myData;

  public TreeTransfer(Class data) {
    myData = data;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // TransferHandler
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public int getSourceActions(JComponent c) {
    return DnDConstants.ACTION_COPY_OR_MOVE;
  }

  @Override
  protected Transferable createTransferable(JComponent c) {
    return this;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Transferable
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public DataFlavor[] getTransferDataFlavors() {
    return new DataFlavor[]{DATA_FLAVOR};
  }

  @Override
  public boolean isDataFlavorSupported(DataFlavor flavor) {
    return flavor.equals(DATA_FLAVOR);
  }

  @Override
  public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
    return myData;
  }
}