// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package ops3

import types.TypeB
import types.TypeC
import types.TypeD

operator fun TypeD.get(b: TypeB): TypeC = TypeC()
