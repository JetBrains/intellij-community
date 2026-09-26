#!/usr/bin/env bash
# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

# Bash-only entry point for the rg (ripgrep) wrapper. Delegates to the polyglot
# rg.cmd, which keeps version/checksum config in one place. Use this from bash
# callers, including Git Bash on Windows, where invoking rg.cmd directly causes
# MSYS to hand off to native cmd.exe and drop bash-side quoting (paths with
# spaces). Native Windows callers should keep using rg.cmd.

set -eu
root="$(cd "$(dirname "$0")" && pwd)"
exec bash "$root/rg.cmd" "$@"
