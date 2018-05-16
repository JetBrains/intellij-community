import os

import thriftpy

# dynamically import console.thrift classes into `console_thrift` module
console_thrift = thriftpy.load(os.path.join(os.path.dirname(os.path.realpath(__file__)), "console.thrift"), module_name="console_thrift")
