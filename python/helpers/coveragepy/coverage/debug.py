"""Control of and utilities for debugging."""

import os


# When debugging, it can be helpful to force some options, especially when
# debugging the configuration mechanisms you usually use to control debugging!
# This is a list of forced debugging options.
FORCED_DEBUG = []


class DebugControl(object):
    """Control and output for debugging."""

    def __init__(self, options, output):
        """Configure the options and output file for debugging."""
        self.options = options
        self.output = output

    def should(self, option):
        """Decide whether to output debug information in category `option`."""
        return (option in self.options or option in FORCED_DEBUG)

    def write(self, msg):
        """Write a line of debug output."""
        if self.should('pid'):
            msg = "pid %5d: %s" % (os.getpid(), msg)
        self.output.write(msg+"\n")
        self.output.flush()

    def write_formatted_info(self, info):
        """Write a sequence of (label,data) pairs nicely."""
        for line in info_formatter(info):
            self.write(" %s" % line)


def info_formatter(info):
    """Produce a sequence of formatted lines from info.

    `info` is a sequence of pairs (label, data).  The produced lines are
    nicely formatted, ready to print.

    """
    label_len = max([len(l) for l, _d in info])
    for label, data in info:
        if data == []:
            data = "-none-"
        if isinstance(data, (list, tuple)):
            prefix = "%*s:" % (label_len, label)
            for e in data:
                yield "%*s %s" % (label_len+1, prefix, e)
                prefix = ""
        else:
            yield "%*s: %s" % (label_len, label, data)
