// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * Say, you need to create a new project for framework "Foo".
 * There are three items to implement:
 * <p/>
 * {@link com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificSettings} is a something that stores settings and generates a project
 * based on them (and other things like SDK and module).
 * <p/>
 * {@link com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificUI} is a class that binds settings, mentioned above, to the Kotlin DSL UI panel.
 * <p/>
 * {@link com.jetbrains.python.newProjectWizard.PyV3ProjectBaseGenerator} connects these two and must be registered as EP.
 *
 *
 */
package com.jetbrains.python.newProjectWizard;