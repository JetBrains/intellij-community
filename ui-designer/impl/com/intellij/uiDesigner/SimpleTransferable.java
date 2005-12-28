package com.intellij.uiDesigner;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public final class SimpleTransferable<T> implements Transferable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.SimpleTransferable");
  private static Map<String, DataFlavor> ourDataFlavorMap = new HashMap<String, DataFlavor>();

  private final T myDataProxy;
  private Class<T> myDataClass;

  public SimpleTransferable(final T data, final Class<T> dataClass) {
    myDataProxy = data;
    myDataClass = dataClass;
  }

  @Nullable
  public Object getTransferData(final DataFlavor flavor) {
    try {
      if (!getDataFlavor(myDataClass).equals(flavor)) {
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
        //noinspection HardCodedStringLiteral
        result = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + dataClass.getName());
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
      return new DataFlavor[]{ getDataFlavor(myDataClass) };
    }
    catch(Exception ex) {
      LOG.error(ex);
      return new DataFlavor[0];
    }
  }

  public boolean isDataFlavorSupported(final DataFlavor flavor) {
    try {
      return flavor.equals(getDataFlavor(myDataClass));
    }
    catch(Exception ex) {
      LOG.error(ex);
      return false;
    }
  }

  @Nullable public static <T> T getData(Transferable transferable, Class<T> dataClass) {
    try {
      final Object transferData = transferable.getTransferData(getDataFlavor(dataClass));
      if (!dataClass.isInstance(transferData)) {
        return null;
      }
      //noinspection unchecked
      return (T) transferData;
    }
    catch (Exception e) {
      LOG.error(e);
      return null;
    }
  }
}
