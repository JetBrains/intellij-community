package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

import java.io.File;

/**
 * @author yole
 * @since 7.0
 */
public interface CheckoutListener {
  ExtensionPointName<CheckoutListener> EP_NAME = ExtensionPointName.create("com.intellij.checkoutListener");
  
  boolean processCheckedOutDirectory(Project project, File directory);
}