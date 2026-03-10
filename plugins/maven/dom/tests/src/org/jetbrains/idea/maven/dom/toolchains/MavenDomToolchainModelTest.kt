// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
      </toolchains>""")

    val sdk = IdeaTestFixtureFactory.getFixtureFactory().createMockJdk("17", "/path/to/mock/jdk", JavaSdk.getInstance())
    addIntoToolchainsFile(project, path, sdk)
  }
