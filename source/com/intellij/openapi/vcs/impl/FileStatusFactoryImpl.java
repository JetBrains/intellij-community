package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusFactory;
import com.intellij.openapi.vcs.checkin.DifferenceType;
import com.intellij.openapi.vcs.checkin.impl.DifferenceTypeImpl;

import java.util.List;
import java.util.ArrayList;
import java.awt.Color;


public class FileStatusFactoryImpl implements FileStatusFactory {
  private final List<FileStatus> myStatuses = new ArrayList<FileStatus>();

  public FileStatus createFileStatus(String id, String description, Color color) {
    FileStatusImpl result = new FileStatusImpl(id, ColorKey.createColorKey("FILESTATUS_" + id, color), description);
    myStatuses.add(result);
    return result;
  }

  public DifferenceType createDifferenceType(String id,
                                             FileStatus fileStatus,
                                             final TextAttributesKey textColorKey,
                                             final Color backgroundColor, Color activeBgColor) {
    return new DifferenceTypeImpl(id, fileStatus, activeBgColor, backgroundColor) {
      public TextAttributesKey getDiffColor(int index) {
        return textColorKey;
      }

      public TextAttributesKey getColor() {
        return textColorKey;
      }
    };
  }

  public DifferenceType createDifferenceTypeInserted() {
    return createDifferenceType("Added",
                                FileStatus.ADDED,
                                DiffColors.DIFF_INSERTED,
                                DiffColors.DIFF_ABSENT,
                                DiffColors.DIFF_INSERTED,
                                null, new Color(125, 223, 125));
  }

  public DifferenceType createDifferenceTypeNotChanged() {
    return createDifferenceType("Not changed",
                                FileStatus.NOT_CHANGED,
                                null,
                                null, Color.white);
  }

  public DifferenceType createDifferenceTypeDeleted() {
    return createDifferenceType("Deleted",
                                FileStatus.DELETED,
                                DiffColors.DIFF_DELETED,
                                DiffColors.DIFF_DELETED,
                                DiffColors.DIFF_ABSENT,
                                null, new Color(157, 157, 157));
  }

  public DifferenceType createDifferenceTypeModified() {
    return createDifferenceType("Modified",
                                FileStatus.MODIFIED,
                                DiffColors.DIFF_MODIFIED,
                                null, new Color(129, 164, 244));
  }

  public DifferenceType createDifferenceType(String id,
                                             FileStatus fileStatus,
                                             final TextAttributesKey mainTextColorKey,
                                             final TextAttributesKey leftTextColorKey,
                                             final TextAttributesKey rightTextColorKey,
                                             Color activeBgColor, Color background) {
    return new DifferenceTypeImpl(id, fileStatus, background, activeBgColor) {
      public TextAttributesKey getDiffColor(int index) {
        if (index == 0) {
          return leftTextColorKey;
        }
        else {
          return rightTextColorKey;
        }
      }

      public TextAttributesKey getColor() {
        return mainTextColorKey;
      }
    };
  }

  public FileStatus[] getAllFileStatuses() {
    return myStatuses.toArray(new FileStatus[myStatuses.size()]);
  }
}