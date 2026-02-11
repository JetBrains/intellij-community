// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository.test

import org.junit.Ignore
import org.junit.runner.RunWith
import org.junit.runners.Suite

@Ignore("Manual only")
@RunWith(Suite::class)
@Suite.SuiteClasses(SettingsRepositoryGitTest::class, OverwriteRemote::class, BareGitTest::class, LoadTest::class)
class IcsTestSuite