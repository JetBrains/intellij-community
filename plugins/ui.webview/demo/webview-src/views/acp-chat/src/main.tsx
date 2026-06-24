// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { createRoot } from "react-dom/client"
import { ChatView } from "./components/ChatView"

const container = document.getElementById("root")
if (container) {
  createRoot(container).render(<ChatView />)
}
