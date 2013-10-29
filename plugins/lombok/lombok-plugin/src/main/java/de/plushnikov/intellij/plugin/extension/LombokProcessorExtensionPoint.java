package de.plushnikov.intellij.plugin.extension;

import com.intellij.openapi.extensions.ExtensionPointName;
import de.plushnikov.intellij.plugin.processor.Processor;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Date: 21.07.13 Time: 12:54
 */
public class LombokProcessorExtensionPoint {
  public static final ExtensionPointName<Processor> EP_NAME = ExtensionPointName.create("Lombook Plugin.processor");

  private static Collection<String> LOMBOK_ANNOTATIONS;

  public static Collection<String> getAllOfProcessedLombokAnnotation() {
    if (null != LOMBOK_ANNOTATIONS) {
      return LOMBOK_ANNOTATIONS;
    }

    Collection<String> arrayList = new ArrayList<String>();
    for (Processor processor : EP_NAME.getExtensions()) {
      arrayList.add(processor.getSupportedAnnotation());
    }

    LOMBOK_ANNOTATIONS = arrayList;
    return LOMBOK_ANNOTATIONS;
  }

}
