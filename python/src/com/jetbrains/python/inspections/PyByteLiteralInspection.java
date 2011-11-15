package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.actions.AddEncodingQuickFix;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import static com.jetbrains.python.psi.FutureFeature.UNICODE_LITERALS;

/**
 * @author Alexey.Ivanov
 */
public class PyByteLiteralInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.byte.literal");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
      PsiFile file = node.getContainingFile(); // can't cache this in the instance, alas
      if (file == null) return;
      boolean default_bytes = false;
      if (file instanceof PyFile) {
        PyFile pyfile = (PyFile)file;
        default_bytes = (!UNICODE_LITERALS.requiredAt(pyfile.getLanguageLevel()) &&
                         !pyfile.hasImportFromFuture(UNICODE_LITERALS)
        );
      }

      final String charsetString = PythonFileType.getCharsetFromEncodingDeclaration(file.getText());
      try {
        if (charsetString != null && !Charset.forName(charsetString).equals(Charset.forName("US-ASCII")))
          default_bytes = false;
      } catch (UnsupportedCharsetException exception) {}

      boolean hasProblem = false;
      char first_char = Character.toLowerCase(node.getText().charAt(0));
      if (first_char == 'b' || (default_bytes && first_char != 'u')) {
        String value = node.getStringValue();
        int length = value.length();
        for (int i = 0; i < length; ++i) {
          char c = value.charAt(i);
          if (((int) c) > 255) {
            hasProblem = true;
            break;
          }
        }
      }
      if (hasProblem) {
        if (charsetString != null)
          registerProblem(node, "Byte literal contains characters > 255");
        else
          registerProblem(node, "Byte literal contains characters > 255", new AddEncodingQuickFix(defaultEncoding));
      }
    }
  }

  private static String[] ourPossibleEncodings = new String[]{"ascii", "big5", "big5hkscs", "cp037", "cp424", "cp437", "cp500", "cp720", "cp737",
    "cp775", "cp850", "cp852", "cp855", "cp856", "cp857", "cp858", "cp860", "cp861", "cp862", "cp863", "cp864", "cp865", "cp866", "cp869", "cp874",
    "cp875", "cp932", "cp949", "cp950", "cp1006", "cp1026", "cp1140", "cp1250", "cp1251", "cp1252", "cp1253", "cp1254", "cp1255", "cp1256", "cp1257",
    "cp1258", "euc-jp", "euc-jis-2004", "euc-kr", "gb2312", "gbk", "gb18030", "hz", "iso2022-jp", "iso2022-jp-1", "iso2022-jp-2", "iso2022-jp-2004",
    "iso2022-jp-3", "iso2022-jp-ext", "iso2022-kr", "latin-1", "iso8859-2", "iso8859-3", "iso8859-4", "iso8859-5", "iso8859-6", "iso8859-7",
    "iso8859-8", "iso8859-9", "iso8859-10", "iso8859-13", "iso8859-14", "iso8859-15", "iso8859-16", "johab", "koi8-r", "koi8-u", "mac-cyrillic",
    "mac-greek", "mac-iceland", "mac-latin2", "mac-roman", "mac-turkish", "ptcp154", "shift-jis", "shift-jis-2004", "shift-jisx0213", "utf-32",
    "utf-32-be", "utf-32-le", "utf-16", "utf-16-be", "utf-16-le", "utf-7", "utf-8", "utf-8-sig"};

  public String defaultEncoding = "utf-8";
  @Override
  public JComponent createOptionsPanel() {
    final JPanel versionPanel = new JPanel(new BorderLayout());

    final JComboBox comboBox = new JComboBox(ourPossibleEncodings);
    comboBox.setSelectedItem(defaultEncoding);

    comboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JComboBox cb = (JComboBox)e.getSource();
        defaultEncoding = (String)cb.getSelectedItem();
      }
    });

    versionPanel.add(new JLabel("Select default encoding: "), BorderLayout.WEST);
    versionPanel.add(comboBox);
    return versionPanel;
  }
}
