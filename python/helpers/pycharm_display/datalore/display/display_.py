import json
import os
import socket
import struct
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from .supported_data_type import _standardize_value

__all__ = ['display']

HOST = "http://127.0.0.1"
PORT = os.getenv("PYCHARM_DISPLAY_PORT")
PORT = int(PORT) if PORT is not None else None
PORT = PORT if PORT != -1 else None


def display(data):
    if PORT and data is not None:
        repr_display_attr = getattr(data, '_repr_display_', None)
        if callable(repr_display_attr):
            message_spec = repr_display_attr()
            assert len(message_spec) == 2, 'Tuple length expected is 2 but was %d' % len(message_spec)

            message_spec = _standardize_value(message_spec)
            _send_display_message({
                    'type': message_spec[0],
                    'body': message_spec[1]
                })
            return

    # just print to python console
    print(repr(data))


def _send_display_message(message_spec):
    serialized = json.dumps(message_spec)
    buffer = serialized.encode()
    try:
        url = HOST + ":" + str(PORT) + "/api/python.scientific"
        urlopen(url, buffer)
    except OSError as _:
        # nothing bad. It just means, that our tool window doesn't run yet
        pass
