from socket import AF_INET
from socket import SOCK_STREAM
from socket import socket
import time
import sys
import traceback
import StringIO

import yappi

from prof_io import ProfWriter, ProfReader
from pydevd_utils import save_main_module
import pydev_imports
from prof_data import copy_fields
from profiler_protocol_pb2 import ProfilerResponse, Stats


def StartClient(host, port):
    """ connects to a host/port """

    s = socket(AF_INET, SOCK_STREAM)

    MAX_TRIES = 100
    i = 0
    while i < MAX_TRIES:
        try:
            s.connect((host, port))
        except:
            i += 1
            time.sleep(0.2)
            continue
        return s

    sys.stderr.write("Could not connect to %s: %s\n" % (host, port))
    sys.stderr.flush()
    traceback.print_exc()
    sys.exit(1)  # TODO: is it safe?


class Profiler(object):
    def __init__(self):
        pass

    def connect(self, host, port):
        s = StartClient(host, port)

        self.initializeNetwork(s)

    def initializeNetwork(self, sock):
        try:
            sock.settimeout(None)  # infinite, no timeouts from now on - jython does not have it
        except:
            pass
        self.writer = ProfWriter(sock)
        self.reader = ProfReader(sock, self)
        self.writer.start()
        self.reader.start()

        time.sleep(0.1)  # give threads time to start

    def process(self, message):
        if message.HasField('ystats_string'):
            self.stats_string(message.id)
        elif message.HasField('ystats'):
            self.func_stats(message.id)
        else:
            raise AssertionError("malformed request")

    def run(self, file):
        m = save_main_module(file, 'run_profiler')
        globals = m.__dict__
        try:
            globals['__builtins__'] = __builtins__
        except NameError:
            pass  # Not there on Jython...

        self.start_profiling()

        pydev_imports.execfile(file, globals, globals)  # execute the script

        # self.stats_string()

        time.sleep(10)

    def start_profiling(self):
        yappi.start(profile_threads=False)

    def stats_string(self, id):
        output = StringIO.StringIO()
        yappi.get_func_stats().print_all(out=output)
        m = ProfilerResponse()
        m.id = id
        m.ystats_string = output.getvalue()
        self.writer.addCommand(m)

    def func_stats(self, id):
        yfunc_stats = yappi.get_func_stats()
        m = ProfilerResponse()
        m.id = id
        ystats = Stats()

        for fstat in yfunc_stats:
            func_stat = ystats.func_stats.add()
            copy_fields(func_stat, fstat)
        m.ystats.CopyFrom(ystats)
        self.writer.addCommand(m)


if __name__ == '__main__':

    host = sys.argv[1]
    port = int(sys.argv[2])
    file = sys.argv[3]

    profiler = Profiler()

    try:
        profiler.connect(host, port)
    except:
        sys.stderr.write("Could not connect to %s: %s\n" % (host, port))
        traceback.print_exc()
        sys.exit(1)

    profiler.run(file)




