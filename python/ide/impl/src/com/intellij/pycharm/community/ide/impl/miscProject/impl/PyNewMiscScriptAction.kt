// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject.impl

import com.intellij.platform.ide.nonModalWelcomeScreen.actions.NewEmptyFileAction
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.jetbrains.python.parser.icons.PythonParserIcons
import java.util.function.Supplier

internal class PyNewMiscScriptAction :
  NewEmptyFileAction("Python", Supplier { PyCharmCommunityCustomizationBundle.message("misc.script.text") }, PythonParserIcons.PythonFile)