import json
import os

import sys
import traceback

from .supported_data_type import _standardize_value
from .display_log import debug

IS_PY3K = True
if sys.version_info[0] < 3:
    IS_PY3K = False

if IS_PY3K:
    import urllib.request as urllib_request
else:
    import urllib2 as urllib_request

__all__ = ['display']

HOST = os.getenv("PYCHARM_DISPLAY_HOST", "http://127.0.0.1")
PORT_ENV = int(os.getenv("PYCHARM_DISPLAY_PORT", "-1"))
PORT = PORT_ENV
if PORT == -1:
    PORT = None
PYCHARM_DISPLAY_HTTP_PROXY = os.getenv("PYCHARM_DISPLAY_HTTP_PROXY", None)


def display(data):
    if not PORT:
        debug("Error: Can't display plot, PORT value is %s" % PORT_ENV)
    if data is None:
        debug("Error: Can't display empty data")
    if PORT and data is not None:
        repr_display_attr = getattr(data, '_repr_display_', None)
        if callable(repr_display_attr):
            message_spec = repr_display_attr()
            if len(message_spec) != 2:
                debug('Error: Tuple length expected is 2 but was %d' % len(message_spec))
                return

            message_spec = _standardize_value(message_spec)
            _send_display_message({
                    'type': message_spec[0],
                    'body': message_spec[1]
                })
            return
        else:
            debug("Error: '_repr_display_' isn't callable")

    # just print to python console
    print(repr(data))


def try_empty_proxy(buffer):
    empty_proxy = urllib_request.ProxyHandler({})
    opener = urllib_request.build_opener(empty_proxy)
    urllib_request.install_opener(opener)
    try:
        url = HOST + ":" + str(PORT) + "/api/python.scientific"
        urllib_request.urlopen(url, buffer)
    except:
        sys.stderr.write("Error: failed to send plot to %s:%s\n" % (HOST, PORT))
        traceback.print_exc()
        sys.stderr.flush()
    finally:
        default_opener = urllib_request.build_opener()
        urllib_request.install_opener(default_opener)


def _send_display_message(message_spec):
    serialized = json.dumps(message_spec)
    buffer = serialized.encode()
    try:
        debug("Sending display message to %s:%s" % (HOST, PORT))
        url = HOST + ":" + str(PORT) + "/api/python.scientific"

        if PYCHARM_DISPLAY_HTTP_PROXY is not None:
            debug("Using HTTP proxy %s" % PYCHARM_DISPLAY_HTTP_PROXY)
            proxy_handler = urllib_request.ProxyHandler(
                {'http': PYCHARM_DISPLAY_HTTP_PROXY}
            )
            opener = urllib_request.build_opener(proxy_handler)
            opener.open(url, buffer)
        else:
            urllib_request.urlopen(url, buffer)
    except:
        # urllib will auto-detect proxy settings and use those, so it might break connection to localhost
        debug("Retry with empty proxy")
        try_empty_proxy(buffer)
