// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports.mlapi

import com.jetbrains.ml.MLUnit
import com.jetbrains.python.codeInsight.imports.ImportCandidateHolder

val MLUnitImportCandidate = MLUnit<ImportCandidateHolder>("import_candidate")

val MLUnitImportCandidatesList = MLUnit<List<ImportCandidateHolder>>("import_candidates_list")
