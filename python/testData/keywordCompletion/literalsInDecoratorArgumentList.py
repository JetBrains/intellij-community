#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

def decorator(arg):
    def wrapper(func):
        return func

    return wrapper


@decorator([Tr<caret>])
def foo():
    pass
