import os
import sys
import time
import traceback
import argparse
from socket import AF_INET
from socket import SOCK_STREAM
from socket import socket

from _prof_imports import ProfilerResponse
from prof_io import ProfWriter, ProfReader
from prof_util import generate_snapshot_filepath, stats_to_response, get_snapshot_basepath, save_main_module, execfile, get_fullname

base_snapshot_path = os.getenv('PYCHARM_SNAPSHOT_PATH')
remote_run = bool(os.getenv('PYCHARM_REMOTE_RUN', ''))
send_stat_flag = bool(os.getenv('PYCHARM_SEND_STAT', ''))

def start_client(host, port):
    """ connects to a host/port """

    s = socket(AF_INET, SOCK_STREAM)

    MAX_TRIES = 100
    for i in range(MAX_TRIES):
        try:
            s.connect((host, port))
            return s
        except:
            time.sleep(0.2)
            continue

    sys.stderr.write(f"Could not connect to {host}: {port}\n")
    sys.stderr.flush()
    traceback.print_exc()
    sys.exit(1)

class Profiler:
    def __init__(self):
        try:
            import vmprof_profiler
            self.profiling_backend = vmprof_profiler.VmProfProfile()
            self.profiling_backend.basepath = get_snapshot_basepath(base_snapshot_path, remote_run)
            print('Starting vmprof profiler\n')
        except ImportError:
            try:
                import yappi_profiler
                self.profiling_backend = yappi_profiler.YappiProfile()
                print('Starting yappi profiler\n')
            except ImportError:
                import cProfile
                self.profiling_backend = cProfile.Profile()
                print('Starting cProfile profiler\n')

    def connect(self, host, port):
        s = start_client(host, port)
        self.initialize_network(s)

    def initialize_network(self, sock):
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
            self.save_snapshot(
                message.id,
                generate_snapshot_filepath(message.save_snapshot.filepath, remote_run, self.snapshot_extension()),
                remote_run or send_stat_flag
            )
        else:
            raise AssertionError(f"Unknown request {dir(message)}")

    def run(self, file, package=None):
        m = save_main_module(file, 'run_profiler')
        globals = m.__dict__
        globals['__package__'] = package
        try:
            globals['__builtins__'] = __builtins__
        except NameError:
            pass  # Not there on Jython...

        self.start_profiling()
        try:
            execfile(file, globals, globals)  # execute the script
        finally:
            self.stop_profiling()
            self.save_snapshot(
                0,
                generate_snapshot_filepath(base_snapshot_path, remote_run, self.snapshot_extension()),
                remote_run or send_stat_flag
            )

    def start_profiling(self):
        self.profiling_backend.enable()

    def stop_profiling(self):
        self.profiling_backend.disable()

    def get_stats(self):
        self.profiling_backend.create_stats()
        return self.profiling_backend.stats

    def has_tree_stats(self):
        return hasattr(self.profiling_backend, 'tree_stats_to_response')

    def tree_stats_to_response(self, filename, response):
        return self.profiling_backend.tree_stats_to_response(filename, response)

    def snapshot_extension(self):
        if hasattr(self.profiling_backend, 'snapshot_extension'):
            return self.profiling_backend.snapshot_extension()
        return '.pstat'

    def dump_snapshot(self, filename):
        directory = os.path.dirname(filename)
        if not os.path.exists(directory):
            os.makedirs(directory)
        self.profiling_backend.dump_stats(filename)
        return filename

    def save_snapshot(self, id, filename, send_stat=False):
        self.stop_profiling()

        if filename is not None:
            filename = self.dump_snapshot(filename)
            print(f'Snapshot saved to {filename}')

        if not send_stat:
            response = ProfilerResponse(id=id, snapshot_filepath=filename)
        else:
            response = ProfilerResponse(id=id)
            stats_to_response(self.get_stats(), response)
            if self.has_tree_stats():
                self.tree_stats_to_response(filename, response)

        self.writer.addCommand(response)
        self.start_profiling()

def setup_module_execution(module_name):
    package_path = get_fullname(module_name)
    if package_path is None:
        exit_with_error(f"No module named {module_name}")

    main_filename = os.path.join(os.path.dirname(package_path), '__main__.py')
    package = module_name

    if os.path.exists(main_filename):
        return main_filename, package, os.path.dirname(os.path.dirname(main_filename))

    if os.path.exists(package_path):
        return package_path, ".".join(module_name.split(".")[:-1]), os.path.dirname(os.path.dirname(package_path))

    exit_with_error(f"No module named {module_name}")

def exit_with_error(message):
    sys.stderr.write(message + "\n")
    sys.exit(1)

def parse_arguments():
    parser = argparse.ArgumentParser(description='Python Profiler')
    parser.add_argument('host', help='Host to connect to')
    parser.add_argument('port', type=int, help='Port number to connect to')

    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('-m', '--module', help='Module to profile')
    group.add_argument('file', nargs='?', help='File to profile')

    args, remaining = parser.parse_known_args()
    return args, remaining

def main():
    args, remaining = parse_arguments()

    if args.module:
        file, package, add_to_path = setup_module_execution(args.module)
        sys.argv = [args.module] + remaining
    else:
        file = args.file
        package = None
        add_to_path = os.path.dirname(file)
        sys.argv = [args.file] + remaining

    profiler = Profiler()
    try:
        profiler.connect(args.host, args.port)
    except:
        sys.stderr.write(f"Could not connect to {args.host}: {args.port}\n")
        traceback.print_exc()
        sys.exit(1)

    sys.path.insert(0, add_to_path)
    profiler.run(file, package=package)

if __name__ == '__main__':
    main()