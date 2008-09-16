package com.intellij.openapi.vcs.checkout;

import java.io.File;

/**
 * author: lesya
 */
public abstract class CheckoutStrategy implements Comparable{
  private final File mySelectedLocation;
  private final File myCvsPath;
  private final boolean myIsForFile;

  public CheckoutStrategy(File selectedLocation, File cvsPath, boolean isForFile) {
    mySelectedLocation = selectedLocation;
    myCvsPath = cvsPath;
    myIsForFile = isForFile;
  }

  public static CheckoutStrategy[] createAllStrategies(File selectedLocation, File cvsPath, boolean isForFile){
    return new CheckoutStrategy[]{
      new SimpleCheckoutStrategy(selectedLocation, cvsPath, isForFile),
      new CheckoutFolderToTheSameFolder(selectedLocation, cvsPath, isForFile),
      new CreateDirectoryForFolderStrategy(selectedLocation, cvsPath, isForFile),
      new CheckoutToTheDirectoryWithModuleName(selectedLocation, cvsPath, isForFile)
    };
  }

  public int compareTo(Object o) {
    if (o instanceof CheckoutStrategy){
      return getResult().compareTo(((CheckoutStrategy)o).getResult());
    }
    return 0;
  }

  public File getSelectedLocation() {
    return mySelectedLocation;
  }

  public File getCvsPath() {
    return myCvsPath;
  }

  public boolean isForFile() {
    return myIsForFile;
  }

  public abstract File getResult();
  public abstract boolean useAlternativeCheckoutLocation();

  public abstract File getCheckoutDirectory();
}
