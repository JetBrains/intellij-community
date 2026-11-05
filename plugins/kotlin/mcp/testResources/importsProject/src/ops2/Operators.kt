// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package ops2

import types.TypeB
import types.TypeC
import types.TypeG

operator fun TypeG.get(b: TypeB): TypeC = TypeC()
