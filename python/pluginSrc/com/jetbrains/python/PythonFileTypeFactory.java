package com.jetbrains.python;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.ultimate.PluginVerifier;
import com.intellij.ultimate.UltimateVerifier;
import com.jetbrains.pyqt.QtTranslationsFileType;
import com.jetbrains.pyqt.QtUIFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Plugin version of file type factory - uses UltimateVerifier.
 *
 * @author yole
 */
public class PythonFileTypeFactory extends FileTypeFactory {
  public PythonFileTypeFactory(UltimateVerifier verifier) {
    PluginVerifier.verifyUltimatePlugin(verifier);
  }

  public void createFileTypes(@NonNls @NotNull final FileTypeConsumer consumer) {
    consumer.consume(PythonFileType.INSTANCE, "py;pyw;");
    consumer.consume(QtUIFileType.INSTANCE, "ui");
    consumer.consume(QtTranslationsFileType.INSTANCE, "ts");
  }
}
