// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public final class SimpleTransferable<T> implements Transferable {
  private static final Logger LOG = Logger.getInstance(SimpleTransferable.class);
  private static final Map<String, DataFlavor> ourDataFlavorMap = new HashMap<>();

  private final T myDataProxy;
  private final Class<T> myDataClass;
  private final DataFlavor myDataFlavor;

  public SimpleTransferable(final T data, final Class<T> dataClass) {
    myDataProxy = data;
    myDataClass = dataClass;
    myDataFlavor = getDataFlavor(myDataClass);
  }

  public SimpleTransferable(final T data, final Class<T> dataClass, DataFlavor flavor) {
    myDataProxy = data;
    myDataClass = dataClass;
    myDataFlavor = flavor;
  }

  @Override
  public @NotNull Object getTransferData(final DataFlavor flavor) throws UnsupportedFlavorException {
    try {
      if (myDataFlavor.equals(flavor)) {
        return myDataProxy;
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
    throw new UnsupportedFlavorException(flavor);
  }

  private static <T> DataFlavor getDataFlavor(final Class<T> dataClass) {
    DataFlavor result = ourDataFlavorMap.get(dataClass.getName());
    if (result == null) {
      try {
        result = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType);
        ourDataFlavorMap.put(dataClass.getName(), result);
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
    return result;
  }

  @Override
  public DataFlavor[] getTransferDataFlavors() {
    try {
      return new DataFlavor[]{ myDataFlavor };
    }
    catch(Exception ex) {
      LOG.error(ex);
      return new DataFlavor[0];
    }
  }

  @Override
  public boolean isDataFlavorSupported(final DataFlavor flavor) {
    try {
      return flavor.equals(myDataFlavor);
    }
    catch(Exception ex) {
      LOG.error(ex);
      return false;
    }
  }

  public static @Nullable <T> T getData(Transferable transferable, Class<T> dataClass) {
    try {
      final DataFlavor dataFlavor = getDataFlavor(dataClass);
      if (!transferable.isDataFlavorSupported(dataFlavor)) {
        return null;
      }
      final Object transferData = transferable.getTransferData(dataFlavor);
      if (!dataClass.isInstance(transferData)) {
        return null;
      }
      //noinspection unchecked
      return (T) transferData;
    }
    catch(IOException e) {
      return null;
    }
    catch (Exception e) {
      LOG.error(e);
      return null;
    }
  }
}
