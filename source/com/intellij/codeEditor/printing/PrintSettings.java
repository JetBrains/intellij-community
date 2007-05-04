package com.intellij.codeEditor.printing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 *
 */
public class PrintSettings implements NamedJDOMExternalizable, ExportableApplicationComponent {
  @NonNls public String PAPER_SIZE = "A4";

  public boolean COLOR_PRINTING = false;
  public boolean SYNTAX_PRINTING = true;
  public boolean PRINT_AS_GRAPHICS = true;

  public boolean PORTRAIT_LAYOUT = true;

  @NonNls public String FONT_NAME = "monospaced";
  public int FONT_SIZE = 10;

  public boolean PRINT_LINE_NUMBERS = true;

  public boolean WRAP = true;

  public float TOP_MARGIN = 0.5f;
  public float BOTTOM_MARGIN = 1.0f;
  public float LEFT_MARGIN = 1.0f;
  public float RIGHT_MARGIN = 1.0f;

  public boolean DRAW_BORDER = true;

  public String FOOTER_HEADER_TEXT1 = CodeEditorBundle.message("print.header.default.line.1");
  public String FOOTER_HEADER_PLACEMENT1 = HEADER;
  public String FOOTER_HEADER_ALIGNMENT1 = LEFT;
  public String FOOTER_HEADER_TEXT2 = CodeEditorBundle.message("print.header.default.line.2");
  public String FOOTER_HEADER_PLACEMENT2 = FOOTER;
  public String FOOTER_HEADER_ALIGNMENT2 = CENTER;
  public int FOOTER_HEADER_FONT_SIZE = 8;
  @NonNls public String FOOTER_HEADER_FONT_NAME = "Arial";

  public static final int PRINT_FILE = 1;
  public static final int PRINT_SELECTED_TEXT = 2;
  public static final int PRINT_DIRECTORY = 4;
  private int myPrintScope;
  private boolean myIncludeSubdirectories;

  @NonNls public static final String HEADER = "Header";
  @NonNls public static final String FOOTER = "Footer";

  @NonNls public static final String LEFT = "Left";
  @NonNls public static final String CENTER = "Center";
  @NonNls public static final String RIGHT = "Right";

  public static PrintSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(PrintSettings.class);
  }

  private PrintSettings(){
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public String getExternalFileName() {
    return "print";
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this)};
  }

  @NotNull
  public String getPresentableName() {
    return CodeEditorBundle.message("title.print.settings");
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public int getPrintScope() {
    return myPrintScope;
  }

  public void setPrintScope(int printScope) {
    myPrintScope = printScope;
  }

  public boolean isIncludeSubdirectories() {
    return myIncludeSubdirectories;
  }

  public void setIncludeSubdirectories(boolean includeSubdirectories) {
    myIncludeSubdirectories = includeSubdirectories;
  }

  public String getComponentName() {
    return "PrintSettings";
  }

}
