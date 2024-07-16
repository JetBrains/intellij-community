package com.intellij.tools.apiDump.testData.inheritFromPackageLocal;

class JPackageLocalRunnable implements Runnable {

  @Override
  public void run() { }
}

public class JPublicClassFromPackageLocalRunnable extends JPackageLocalRunnable {
}
