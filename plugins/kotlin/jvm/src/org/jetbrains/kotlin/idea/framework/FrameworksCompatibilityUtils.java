// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.framework;

import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.KotlinJvmBundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class FrameworksCompatibilityUtils {
    private FrameworksCompatibilityUtils() {
    }

    public static void suggestRemoveIncompatibleFramework(
            @NotNull ModifiableRootModel rootModel,
            @NotNull Set<? extends LibraryKind> frameworkLibraryKinds,
            @NotNull String presentableName
    ) {
        List<OrderEntry> existingEntries = new ArrayList<>();

        for (OrderEntry entry : rootModel.getOrderEntries()) {
            if (!(entry instanceof LibraryOrderEntry)) continue;
            Library library = ((LibraryOrderEntry)entry).getLibrary();
            if (library == null) continue;

            for (LibraryKind kind : frameworkLibraryKinds) {
                if (LibraryPresentationManager.getInstance().isLibraryOfKind(Arrays.asList(library.getFiles(OrderRootType.CLASSES)), kind)) {
                    existingEntries.add(entry);
                }
            }
        }

        removeWithConfirm(
                rootModel, existingEntries,
                KotlinJvmBundle.message("frameworks.remove.conflict.question", presentableName),
                KotlinJvmBundle.message("frameworks.remove.conflict.title")
        );
    }

    private static void removeWithConfirm(
            ModifiableRootModel rootModel,
            List<OrderEntry> orderEntries,
            @NlsContexts.DialogMessage String message,
            @NlsContexts.DialogTitle String title
    ) {
        if (!orderEntries.isEmpty()) {
            int result = Messages.showYesNoDialog(message, title, Messages.getWarningIcon());

            if (result == 0) {
                for (OrderEntry entry : orderEntries) {
                    rootModel.removeOrderEntry(entry);
                }
            }
        }
    }
}
