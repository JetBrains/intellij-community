package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import java.util.Comparator;

class UrlComparator implements Comparator<String> {
  public int compare(String url1, String url2) {
    String name1 = url1.substring(url1.lastIndexOf('/') + 1);
    String name2 = url2.substring(url2.lastIndexOf('/') + 1);
    return name1.compareToIgnoreCase(name2);
  }
}
