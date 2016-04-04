import os
import sys
import time
import traceback
from _pydev_bundle import pydev_imports
from _pydevd_bundle.pydevd_utils import save_main_module
from socket import AF_INET
from socket import SOCK_STREAM
from socket import socket

from _prof_imports import ProfilerResponse
from prof_io import ProfWriter, ProfReader
from prof_util import generate_snapshot_filepath, statsToResponse

base_snapshot_path = os.getenv('PYCHARM_SNAPSHOT_PATH')
remote_run = bool(os.getenv('PYCHARM_REMOTE_RUN', ''))

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
        try:
            import yappi_profiler
            self.profiling_backend = yappi_profiler.YappiProfile()
            print('Starting yappi profiler\n')
        except ImportError:
            import cProfile
            self.profiling_backend = cProfile.Profile()
            print('Starting cProfile profiler\n')

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
        self.reader.start()

        time.sleep(0.1)  # give threads time to start

    def process(self, message):
        if hasattr(message, 'save_snapshot'):
            self.save_snapshot(message.id, generate_snapshot_filepath(message.save_snapshot.filepath, remote_run), remote_run)
        else:
            raise AssertionError("Unknown request %s" % dir(message))

    def run(self, file):
        m = save_main_module(file, 'run_profiler')
        globals = m.__dict__
        try:
            globals['__builtins__'] = __builtins__
        except NameError:
            pass  # Not there on Jython...

        self.start_profiling()

        try:
            pydev_imports.execfile(file, globals, globals)  # execute the script
        finally:
            self.stop_profiling()
            self.save_snapshot(0, generate_snapshot_filepath(base_snapshot_path, remote_run), remote_run)

    def start_profiling(self):
        self.profiling_backend.enable()

    def stop_profiling(self):
        self.profiling_backend.disable()

    def get_snapshot(self):
        self.profiling_backend.create_stats()
        return self.profiling_backend.stats

    def dump_snapshot(self, filename):
        dir = os.path.dirname(filename)
        if not os.path.exists(dir):
            os.makedirs(dir)

        self.profiling_backend.dump_stats(filename)
        return filename

    def save_snapshot(self, id, filename, send_stat=False):
        self.stop_profiling()
        if filename is not None:
            filename = self.dump_snapshot(filename)
            print('Snapshot saved to %s' % filename)

        if not send_stat:
            response = ProfilerResponse(id=id, snapshot_filepath=filename)
        else:
            response = ProfilerResponse(id=id)
            statsToResponse(self.get_snapshot(), response)

        self.writer.addCommand(response)
        self.start_profiling()


if __name__ == '__main__':

    host = sys.argv[1]
    port = int(sys.argv[2])
    file = sys.argv[3]

    del sys.argv[0]
    del sys.argv[0]
    del sys.argv[0]

    profiler = Profiler()

    try:
        profiler.connect(host, port)
    except:
        sys.stderr.write("Could not connect to %s: %s\n" % (host, port))
        traceback.print_exc()
        sys.exit(1)

    # add file path to sys.path
    sys.path.insert(0, os.path.split(file)[0])

    profiler.run(file)
