# -*- coding: utf-8 -*-

from __future__ import absolute_import

import logging
import threading

from _shaded_thriftpy.protocol import TBinaryProtocolFactory
from _shaded_thriftpy.transport import (
    TBufferedTransportFactory,
    TTransportException
)


logger = logging.getLogger(__name__)


class TServer(object):
    def __init__(self, processor, trans,
                 itrans_factory=None, iprot_factory=None,
                 otrans_factory=None, oprot_factory=None):
        self.processor = processor
        self.trans = trans

        self.itrans_factory = itrans_factory or TBufferedTransportFactory()
        self.iprot_factory = iprot_factory or TBinaryProtocolFactory()
        self.otrans_factory = otrans_factory or self.itrans_factory
        self.oprot_factory = oprot_factory or self.iprot_factory

    def serve(self):
        pass

    def close(self):
        pass


class TSimpleServer(TServer):
    """Simple single-threaded server that just pumps around one transport."""

    def __init__(self, *args):
        TServer.__init__(self, *args)
        self.closed = False

    def serve(self):
        self.trans.listen()
        while True:
            client = self.trans.accept()
            itrans = self.itrans_factory.get_transport(client)
            otrans = self.otrans_factory.get_transport(client)
            iprot = self.iprot_factory.get_protocol(itrans)
            oprot = self.oprot_factory.get_protocol(otrans)
            try:
                while not self.closed:
                    self.processor.process(iprot, oprot)
            except TTransportException:
                pass
            except Exception as x:
                logger.exception(x)

            itrans.close()
            otrans.close()

    def close(self):
        self.closed = True


class TThreadedServer(TServer):
    """Threaded server that spawns a new thread per each connection."""

    def __init__(self, *args, **kwargs):
        self.daemon = kwargs.pop("daemon", False)
        TServer.__init__(self, *args, **kwargs)
        self.closed = False

    def serve(self):
        self.trans.listen()
        while not self.closed:
            try:
                client = self.trans.accept()
                t = threading.Thread(target=self.handle, args=(client,))
                t.setDaemon(self.daemon)
                t.start()
            except KeyboardInterrupt:
                raise
            except Exception as x:
                logger.exception(x)

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
            logger.exception(x)

        itrans.close()
        otrans.close()

    def close(self):
        self.closed = True
