/**
 * @author Yura Cangea
 */
package com.intellij.openapi.fileChooser.impl;

import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.ex.FileNodeDescriptor;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Comparator;

public final class FileComparator implements Comparator {
  private static final FileComparator INSTANCE = new FileComparator();

  private FileComparator() {
    // empty
  }

  public static FileComparator getInstance() {
    return INSTANCE;
  }

  public int compare(Object o1, Object o2) {
    FileNodeDescriptor nodeDescriptor1 = (FileNodeDescriptor)o1;
    FileNodeDescriptor nodeDescriptor2 = (FileNodeDescriptor)o2;

    int weight1 = getWeight(nodeDescriptor1);
    int weight2 = getWeight(nodeDescriptor2);

    if (weight1 != weight2) {
      return weight1 - weight2;
    }

    return o1.toString().compareToIgnoreCase(o2.toString());
  }

   private static int getWeight(FileNodeDescriptor descriptor) {
     VirtualFile file = ((FileElement)descriptor.getElement()).getFile();
     return file == null || file.isDirectory() ? 0 : 1;
   }
}
