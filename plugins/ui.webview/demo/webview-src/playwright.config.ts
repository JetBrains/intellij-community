// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type { PlaywrightTestConfig } from "@playwright/test"

const projects: NonNullable<PlaywrightTestConfig["projects"]> = [
  {
    name: "chromium",
    use: { browserName: "chromium" },
  },
]

if (process.platform === "win32") {
  projects.push({
    name: "edge",
    use: { browserName: "chromium", channel: "msedge" },
  })
}

export default {
  testDir: "./test",
  testMatch: "**/*.browser.test.ts",
  fullyParallel: true,
  reporter: "list",
  use: {
    trace: "on-first-retry",
  },
  projects,
} satisfies PlaywrightTestConfig
