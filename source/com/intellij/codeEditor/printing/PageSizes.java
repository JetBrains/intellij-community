package com.intellij.codeEditor.printing;

import com.intellij.util.containers.HashMap;

import java.util.ArrayList;

class PageSizes {
  private static ArrayList myPageSizes = null;
  private static HashMap myNamesToPageSizes = null;
  private static final double MM_TO_INCH = 1/25.4;

  public static String[] getNames() {
    init();
    String[] ret = new String[myPageSizes.size()];
    for(int i = 0; i < myPageSizes.size(); i++) {
      PageSize pageSize = (PageSize)myPageSizes.get(i);
      ret[i] = pageSize.name;
    }
    return ret;
  }

  public static Object getItem(String name) {
    init();
    return myNamesToPageSizes.get(name);
  }

  public static double getWidth(String name) {
    init();
    PageSize pageSize = (PageSize)myNamesToPageSizes.get(name);
    if(pageSize == null) {
      return 0;
    }
    return pageSize.width;
  }

  public static double getHeight(String name) {
    init();
    PageSize pageSize = (PageSize)myNamesToPageSizes.get(name);
    if(pageSize == null) {
      return 0;
    }
    return pageSize.height;
  }

  public static String getName(Object item) {
    init();
    if(!(item instanceof PageSize)) {
      return null;
    }
    PageSize pageSize = (PageSize)item;
    return pageSize.name;
  }

  private static void addPageSizeMM(String name, String dimensions, int width, int height) {
    addPageSizeIn(name, dimensions, MM_TO_INCH*width, MM_TO_INCH*height);
  }

  private static void addPageSizeIn(String name, String dimensions, double width, double height) {
    PageSize pageSize = new PageSize();
    pageSize.name = name;
    pageSize.visualName = name + "    (" + dimensions + ")";
    pageSize.width = width;
    pageSize.height = height;
    myPageSizes.add(pageSize);
    myNamesToPageSizes.put(pageSize.name, pageSize);
  }

  private static void init() {
    if(myPageSizes != null) {
      return;
    }
    myPageSizes = new ArrayList();
    myNamesToPageSizes = new HashMap();
    // North American Letter, 8 1/2 x 11 in.
    addPageSizeIn("Letter", "8 1/2 x 11 in", 8.5, 11);
    // A4, 210 x 297 mm.
    addPageSizeMM("A4", "210 x 297 mm", 210, 297);
     // 4A0, 1682 x 2378 mm.
    addPageSizeMM("4A0", "1682 x 2378 mm", 1682, 2378);
     // 2A0, 1189 x 1682 mm.
    addPageSizeMM("2A0", "1189 x 1682 mm", 1189, 1682);
     // A0, 841 x 1189 mm.
    addPageSizeMM("A0", "841 x 1189 mm", 841, 1189);
     // A1, 594 x 841 mm.
    addPageSizeMM("A1", "594 x 841 mm", 594, 841);
     // A2, 420 x 594 mm.
    addPageSizeMM("A2", "420 x 594 mm", 420, 594);
     // A3, 297 x 420 mm.
    addPageSizeMM("A3", "297 x 420 mm", 297, 420);
     // A4, 210 x 297 mm.
//    addPageSizeMM("A4", "210 x 297 mm", 210, 297);
     // A5, 148 x 210 mm.
    addPageSizeMM("A5", "148 x 210 mm", 148, 210);
     // A6, 105 x 148 mm.
    addPageSizeMM("A6", "105 x 148 mm", 105, 148);
     // A7, 74 x 105 mm.
    addPageSizeMM("A7", "74 x 105 mm", 74, 105);
     // A8, 52 x 74 mm.
    addPageSizeMM("A8", "52 x 74 mm", 52, 74);
     // A9, 37 x 52 mm.
    addPageSizeMM("A9", "37 x 52 mm", 37, 52);
     // A10, 26 x 37 mm.
    addPageSizeMM("A10", "26 x 37 mm", 26, 37);
     // B0, 1000 x 1414 mm.
    addPageSizeMM("B0", "1000 x 1414 mm", 1000, 1414);
     // B1, 707 x 1000 mm.
    addPageSizeMM("B1", "707 x 1000 mm", 707, 1000);
     // B2, 500 x 707 mm.
    addPageSizeMM("B2", "500 x 707 mm", 500, 707);
     // B3, 353 x 500 mm.
    addPageSizeMM("B3", "353 x 500 mm", 353, 500);
     // B4, 250 x 353 mm.
    addPageSizeMM("B4", "250 x 353 mm", 250, 353);
     // B5, 176 x 250 mm.
    addPageSizeMM("B5", "176 x 250 mm", 176, 250);
     // B6, 125 x 176 mm.
    addPageSizeMM("B6", "125 x 176 mm", 125, 176);
     // B7, 88 x 125 mm.
    addPageSizeMM("B7", "88 x 125 mm", 88, 125);
     // B8, 62 x 88 mm.
    addPageSizeMM("B8", "62 x 88 mm", 62, 88);
     // B9, 44 x 62 mm.
    addPageSizeMM("B9", "44 x 62 mm", 44, 62);
     // B10, 31 x 44 mm.
    addPageSizeMM("B10", "44 x 62 mm", 31, 44);
     // C0, 917 x 1297 mm.
    addPageSizeMM("C0", "917 x 1297 mm", 917, 1297);
     // C1, 648 x 917 mm.
    addPageSizeMM("C1", "648 x 917 mm", 648, 917);
     // C2, 458 x 648 mm.
    addPageSizeMM("C2", "458 x 648 mm", 458, 648);
     // C3, 324 x 458 mm.
    addPageSizeMM("C3", "324 x 458 mm", 324, 458);
     // C4, 229 x 324 mm.
    addPageSizeMM("C4", "229 x 324 mm", 229, 324);
     // C5, 162 x 229 mm.
    addPageSizeMM("C5", "162 x 229 mm", 162, 229);
     // C6, 114 x 162 mm.
    addPageSizeMM("C6", "114 x 162 mm", 114, 162);
     // C7, 81 x 114 mm.
    addPageSizeMM("C7", "81 x 114 mm", 81, 114);
     // C8, 57 x 81 mm.
    addPageSizeMM("C8", "57 x 81 mm", 57, 81);
     // C9, 40 x 57 mm.
    addPageSizeMM("C9", "40 x 57 mm", 40, 57);
     // C10, 28 x 40 mm.
    addPageSizeMM("C10", "28 x 40 mm", 28, 40);
     // Executive, 7 1/4 x 10 1/2 in.
    addPageSizeIn("Executive", "7 1/4 x 10 1/2 in", 7.25, 10.5);
     // Folio, 8 1/2 x 13 in.
    addPageSizeIn("Folio", "8 1/2 x 13 in", 8.5, 13);
     // Invoice, 5 1/2 x 8 1/2 in.
    addPageSizeIn("Invoice", "5 1/2 x 8 1/2 in", 5.5, 8.5);
     // Ledger, 11 x 17 in.
    addPageSizeIn("Ledger", "11 x 17 in", 11, 17);
     // North American Letter, 8 1/2 x 11 in.
//    addPageSizeIn("Letter", "8 1/2 x 11 in", 8.5, 11);
     // North American Legal, 8 1/2 x 14 in.
    addPageSizeIn("Legal", "8 1/2 x 14 in", 8.5, 14);
     // Quarto, 215 x 275 mm.
    addPageSizeMM("Quarto", "215 x 275 mm", 215, 275);
     // Engineering A, 8 1/2 x 11 in.
    addPageSizeIn("Engineering A", "8 1/2 x 11 in", 8.5, 11);
     // Engineering B, 11 x 17 in.
    addPageSizeIn("Engineering B", "11 x 17 in", 11, 17);
     // Engineering C, 17 x 22 in.
    addPageSizeIn("Engineering C", "17 x 22 in", 17, 22);
     // Engineering D, 22 x 34 in.
    addPageSizeIn("Engineering D", "22 x 34 in", 22, 34);
     // Engineering E, 34 x 44 in.
    addPageSizeIn("Engineering E", "34 x 44 in", 34, 44);
     // North American 10 x 15 in.
    addPageSizeIn("Envelope 10X15", "10 x 15 in", 10, 15);
     // North American 10 x 14 in.
    addPageSizeIn("Envelope 10X14", "10 x 14 in", 10, 14);
     // North American 10 x 13 in.
    addPageSizeIn("Envelope 10X13", "10 x 13 in", 10, 13);
     // North American 9 x 12 in.
    addPageSizeIn("Envelope 9X12", "9 x 12 in", 9, 12);
     // North American 9 x 11 in.
    addPageSizeIn("Envelope 9X11", "9 x 11 in", 9, 11);
     // North American 7 x 9 in.
    addPageSizeIn("Envelope 7X9", "7 x 9 in", 7, 9);
     // North American 6 x 9 in.
    addPageSizeIn("Envelope 6X9", "6 x 9 in", 6, 9);
     // North American #9 Business Envelope  3 7/8 x 8 7/8 in.
    addPageSizeIn("Business Envelope #9", "3 7/8 x 8 7/8 in", 3.875, 8.875);
     // North American #10 Business Envelope 4 1/8 x 9 1/2 in.
    addPageSizeIn("Business Envelope #10", "4 1/8 x 9 1/2 in", 4.125, 9.5);
     // North American #11 Business Envelope 4 1/2 x 10 3/8 in.
    addPageSizeIn("Business Envelope #11", "4 1/2 x 10 3/8 in", 4.5, 10.375);
     // North American #12 Business Envelope 4 3/4 x 11 in.
    addPageSizeIn("Business Envelope #12", "4 3/4 x 11 in in", 4.75, 11);
     // North American #14 Business Envelope 5 x 11 1/2 in.
    addPageSizeIn("Business Envelope #14", "5 x 11 1/2 in", 5, 11.5);
     // Invitation Envelope, 220 x 220 mm.
    addPageSizeMM("Invitation Envelope", "220 x 220 mm", 220, 220);
     // Italy Envelope, 110 x 230 mm.
    addPageSizeMM("Italy Envelope", "110 x 230 mm", 110, 230);
     // Monarch Envelope, 3 7/8 x 7 1/2 in.
    addPageSizeIn("Monarch Envelope", "3 7/8 x 7 1/2 in", 3.875, 7.5);
     // Personal 6 3/4 envelope, 3 5/8 x 6 1/2 in.
    addPageSizeIn("Personal Envelope", "3 5/8 x 6 1/2 in", 3.625, 6.5);

  }

  private static class PageSize {
    public double width;
    public double height;
    public String name;
    public String visualName;

    public String toString() {
      return visualName;
    }
  }
}