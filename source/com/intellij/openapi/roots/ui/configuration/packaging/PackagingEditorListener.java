package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.deployment.ContainerElement;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @author nik
*/
public interface PackagingEditorListener extends EventListener {

  void packagingMethodChanged(@NotNull ContainerElement element);

}
