// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.designer.clipboard;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;


public class SimpleTransferable implements Transferable {
  private static final Logger LOG = Logger.getInstance(SimpleTransferable.class);

  private final Object myData;
  private final DataFlavor myFlavor;

  public SimpleTransferable(Object data, DataFlavor flavor) {
    myData = data;
    myFlavor = flavor;
  }

  @Override
  public DataFlavor[] getTransferDataFlavors() {
    try {
      return new DataFlavor[]{myFlavor};
    }
    catch (Exception ex) {
      LOG.error(ex);
      return new DataFlavor[0];
    }
  }

  @Override
  public boolean isDataFlavorSupported(DataFlavor flavor) {
    try {
      return myFlavor.equals(flavor);
    }
    catch (Exception ex) {
      LOG.error(ex);
      return false;
    }
  }

  @Override
  @NotNull
  public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
    try {
      if (myFlavor.equals(flavor)) {
        return myData;
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
    throw new UnsupportedFlavorException(flavor);
  }

  @Nullable
  public static <T> T getData(Transferable transferable, Class<T> dataClass) {
    try {
      for (DataFlavor dataFlavor : transferable.getTransferDataFlavors()) {
        if (transferable.isDataFlavorSupported(dataFlavor)) {
          Object transferData = transferable.getTransferData(dataFlavor);
          if (dataClass.isInstance(transferData)) {
            return (T)transferData;
          }
        }
      }
    }
    catch (IOException e) {
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return null;
  }
}