blocksize: int
block_size: int
digest_size: int

#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

class sha(object):  # not actually exposed
    name: str
    block_size: int
    digest_size: int
    digestsize: int
    def copy(self) -> sha: ...
    def digest(self) -> str: ...
    def hexdigest(self) -> str: ...
    def update(self, arg: str) -> None: ...

def new(arg: str = ...) -> sha: ...
