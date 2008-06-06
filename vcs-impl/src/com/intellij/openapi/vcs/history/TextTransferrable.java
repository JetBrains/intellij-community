package com.intellij.openapi.vcs.history;

import com.intellij.openapi.diagnostic.Logger;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.StringReader;

public class TextTransferrable implements Transferable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.history.TextTransferrable");

  private final String myHtmlContent;
  private final String myPlainContent;

  private static DataFlavor html;
  private static DataFlavor[] flavors;

  private static DataFlavor[] getFlavours() {
    if (flavors == null) {
      try {
        html = new DataFlavor("text/html;class=java.lang.String");
      }
      catch (ClassNotFoundException e) {
        LOG.error(e);
        html = null;
      }

      flavors = (html == null) ? new DataFlavor[] {DataFlavor.stringFlavor, DataFlavor.plainTextFlavor} :
                new DataFlavor[] {DataFlavor.stringFlavor, DataFlavor.plainTextFlavor, html};
    }
    return flavors;
  }

  public TextTransferrable(final String htmlContent, final String plainContent) {
    myHtmlContent = htmlContent;
    myPlainContent = plainContent;
  }

  public DataFlavor[] getTransferDataFlavors() {
    return getFlavours().clone();
  }

  public boolean isDataFlavorSupported(final DataFlavor flavor) {
    for (DataFlavor flavor1 : getFlavours()) {
      if (flavor.equals(flavor1)) {
        return true;
      }
    }
    return false;
  }

  public Object getTransferData(final DataFlavor flavor) throws UnsupportedFlavorException, IOException {
    if (flavor.equals(html)) {
      return myHtmlContent;
    } else if (flavor.equals(DataFlavor.plainTextFlavor)) {
      return new StringReader(myPlainContent == null ? "" : myPlainContent);
    } else if (flavor.equals(DataFlavor.stringFlavor)) {
      return myPlainContent;
    }
    throw new UnsupportedFlavorException(flavor);
  }
}
