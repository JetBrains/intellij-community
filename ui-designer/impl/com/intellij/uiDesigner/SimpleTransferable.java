package com.intellij.uiDesigner;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;

/**
 * @author yole
 */
public final class SimpleTransferable<T> implements Transferable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.SimpleTransferable");
  private static Map<String, DataFlavor> ourDataFlavorMap = new HashMap<String, DataFlavor>();

  private final T myDataProxy;
  private Class<T> myDataClass;
  private DataFlavor myDataFlavor;

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

  @Nullable
  public Object getTransferData(final DataFlavor flavor) {
    try {
      if (!myDataFlavor.equals(flavor)) {
        return null;
      }
      return myDataProxy;
    }
    catch (Exception e) {
      LOG.error(e);
      return null;
    }
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

  public DataFlavor[] getTransferDataFlavors() {
    try {
      return new DataFlavor[]{ myDataFlavor };
    }
    catch(Exception ex) {
      LOG.error(ex);
      return new DataFlavor[0];
    }
  }

  public boolean isDataFlavorSupported(final DataFlavor flavor) {
    try {
      return flavor.equals(myDataFlavor);
    }
    catch(Exception ex) {
      LOG.error(ex);
      return false;
    }
  }

  @Nullable public static <T> T getData(Transferable transferable, Class<T> dataClass) {
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
