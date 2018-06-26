import os

import _jetbrains_thriftpy

# dynamically import console.thrift classes into `console_thrift` module
console_thrift = _jetbrains_thriftpy.load(os.path.join(os.path.dirname(os.path.realpath(__file__)), "console.thrift"), module_name="console_thrift")
