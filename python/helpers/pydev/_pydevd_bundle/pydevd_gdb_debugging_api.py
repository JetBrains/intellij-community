from _pydevd_bundle.pydevd_comm import start_server, NetCommandFactory, InternalGetFrame, InternalGetVariable


class BlockingWriter:
    def __init__(self, port):
        self.sock = start_server(port)

    def add_command(self, msg):
        self.sock.sendall(bytearray(msg.outgoing, 'utf-8'))

    def commit(self, msg):
        self.sock.sendall(bytearray(msg, 'utf-8'))

    def close(self):
        try:
            self.sock.shutdown(SHUT_RDWR)
        finally:
            self.sock.close()

class BlockingDebugger:
    def __init__(self, port):
        self.cmd_factory = NetCommandFactory()
        self.writer = BlockingWriter(port)

dbg = None

def init_writer(port):
    global dbg
    dbg = BlockingDebugger(port)

def get_frame_variables(seq, thread_id, frame_id):
    InternalGetFrame(seq, thread_id, frame_id).do_it(dbg)

def get_variable(seq, thread_id, frame_id, scope, attrs):
    InternalGetVariable(seq, thread_id, frame_id, scope, attrs).do_it(dbg)

