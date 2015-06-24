package org.jetbrains.settingsRepository.test

import org.junit.runner.RunWith
import org.junit.runners.Suite

RunWith(Suite::class)
Suite.SuiteClasses(GitTest::class, BareGitTest::class, LoadTest::class)
class SettingsRepositoryTestSuite