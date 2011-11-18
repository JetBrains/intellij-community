package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
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
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
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
          registerProblem(node, "Byte literal contains characters > 255", new AddEncodingQuickFix(myDefaultEncoding, myEncodingFormatIndex));
      }
    }
  }

  public String myDefaultEncoding = "utf-8";
  public int myEncodingFormatIndex = 0;
  @Override
  public JComponent createOptionsPanel() {
    final JComboBox defaultEncoding = new JComboBox(PyEncodingUtil.POSSIBLE_ENCODINGS);
    defaultEncoding.setSelectedItem(myDefaultEncoding);

    defaultEncoding.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JComboBox cb = (JComboBox)e.getSource();
        myDefaultEncoding = (String)cb.getSelectedItem();
      }
    });

    final JComboBox encodingFormat = new JComboBox(PyEncodingUtil.ENCODING_FORMAT);

    encodingFormat.setSelectedIndex(myEncodingFormatIndex);
    encodingFormat.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JComboBox cb = (JComboBox)e.getSource();
        myEncodingFormatIndex = cb.getSelectedIndex();
      }
    });

    return PyEncodingUtil.createEncodingOptionsPanel(defaultEncoding, encodingFormat);
  }
}
