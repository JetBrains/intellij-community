// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.webcore.packaging.PackageManagementService;
import com.intellij.webcore.packaging.PackagesNotificationPanel;
import com.intellij.webcore.packaging.PackagingErrorDialog;
import com.jetbrains.python.packaging.ui.PyPackageManagementService;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal

public class PyPackagesNotificationPanel extends PackagesNotificationPanel {

  public PyPackagesNotificationPanel() {
    super(PyPackagesNotificationPanel::showPackageInstallationError);
  }

  public static void showPackageInstallationError(@NotNull @NlsContexts.DialogTitle String title,
                                                  @NotNull PackageManagementService.ErrorDescription description) {
    if (description instanceof PyPackageManagementService.PyPackageInstallationErrorDescription errorDescription) {
      final PyPackageInstallationErrorDialog dialog = new PyPackageInstallationErrorDialog(title, errorDescription);
      dialog.show();
    } else {
      final PackagingErrorDialog dialog = new PackagingErrorDialog(title, description);
      dialog.show();
    }
  }
}
