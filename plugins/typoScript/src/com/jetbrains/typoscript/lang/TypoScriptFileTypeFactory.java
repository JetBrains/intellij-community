package com.jetbrains.typoscript.lang;

import com.intellij.openapi.fileTypes.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author lene
 *         Date: 11.04.12
 */
public class TypoScriptFileTypeFactory extends FileTypeFactory {

  public static final List<? extends FileNameMatcher> FILE_NAME_MATCHERS =
    Collections.unmodifiableList(Arrays.asList(new ExtensionFileNameMatcher(TypoScriptFileType.DEFAULT_EXTENSION),
                                               new ExactFileNameMatcher("setup.txt"),
                                               new ExactFileNameMatcher("constants.txt"),
                                               new ExactFileNameMatcher("ext_conf_template.txt"),
                                               new ExactFileNameMatcher("ext_typoscript_setup.txt"),
                                               new ExactFileNameMatcher("ext_typoscript_constants.txt")));

  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consumer.consume(TypoScriptFileType.INSTANCE, FILE_NAME_MATCHERS.toArray(new FileNameMatcher[FILE_NAME_MATCHERS.size()]));
  }
}