#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

class GetoptError(Exception):
    opt: str
    msg: str
    def __init__(self, msg: str, opt: str = ...) -> None: ...

error = GetoptError

def getopt(args: list[str], shortopts: str, longopts: list[str] = ...) -> tuple[list[tuple[str, str]], list[str]]: ...
def gnu_getopt(args: list[str], shortopts: str, longopts: list[str] = ...) -> tuple[list[tuple[str, str]], list[str]]: ...
