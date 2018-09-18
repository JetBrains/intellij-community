import threading
import traceback

from _shaded_thriftpy.server import TServer
from _shaded_thriftpy.transport import TTransportException


class TSingleThreadedServer(TServer):
    """Server that accepts a single connection and spawns a thread to handle it."""

    def __init__(self, *args, **kwargs):
        self.daemon = kwargs.pop("daemon", False)
        TServer.__init__(self, *args, **kwargs)

    def serve(self):
        self.trans.listen()
        try:
            client = self.trans.accept()
            t = threading.Thread(target=self.handle, args=(client,))
            t.setDaemon(self.daemon)
            t.start()
        except KeyboardInterrupt:
            raise
        except Exception as x:
            traceback.print_exc()

    def handle(self, client):
        itrans = self.itrans_factory.get_transport(client)
        otrans = self.otrans_factory.get_transport(client)
        iprot = self.iprot_factory.get_protocol(itrans)
        oprot = self.oprot_factory.get_protocol(otrans)
        try:
            while True:
                self.processor.process(iprot, oprot)
        except TTransportException:
            pass
        except Exception as x:
            traceback.print_exc()

        itrans.close()
        otrans.close()
