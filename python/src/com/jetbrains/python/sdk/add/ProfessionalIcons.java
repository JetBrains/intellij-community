// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class ProfessionalIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, ProfessionalIcons.class);
  }

  public static final Icon Docker = load("/icons/com/jetbrains/professional/Docker.png"); // 16x16

  public static final Icon DockerCompose = load("/icons/com/jetbrains/professional/DockerCompose.png"); // 16x16

  public static final Icon SSH = load("/icons/com/jetbrains/professional/ssh.png"); // 16x16

  public static final Icon Vagrant = load("/icons/com/jetbrains/professional/vagrant.png"); // 16x16

}
