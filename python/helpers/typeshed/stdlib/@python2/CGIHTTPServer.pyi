#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import SimpleHTTPServer

class CGIHTTPRequestHandler(SimpleHTTPServer.SimpleHTTPRequestHandler):
    cgi_directories: list[str]
    def do_POST(self) -> None: ...
