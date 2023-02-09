# Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0
# For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt

"""Simple value objects for tracking what to do with files."""


class FileDisposition:
    """A simple value type for recording what to do with a file."""

    def __repr__(self):
        return f"<FileDisposition {self.canonical_filename!r}: trace={self.trace}>"


# FileDisposition "methods": FileDisposition is a pure value object, so it can
# be implemented in either C or Python.  Acting on them is done with these
# functions.

def disposition_init(cls, original_filename):
    """Construct and initialize a new FileDisposition object."""
    disp = cls()
    disp.original_filename = original_filename
    disp.canonical_filename = original_filename
    disp.source_filename = None
    disp.trace = False
    disp.reason = ""
    disp.file_tracer = None
    disp.has_dynamic_filename = False
    return disp


def disposition_debug_msg(disp):
    """Make a nice debug message of what the FileDisposition is doing."""
    if disp.trace:
        msg = f"Tracing {disp.original_filename!r}"
        if disp.original_filename != disp.source_filename:
            msg += f" as {disp.source_filename!r}"
        if disp.file_tracer:
            msg += f": will be traced by {disp.file_tracer!r}"
    else:
        msg = f"Not tracing {disp.original_filename!r}: {disp.reason}"
    return msg
