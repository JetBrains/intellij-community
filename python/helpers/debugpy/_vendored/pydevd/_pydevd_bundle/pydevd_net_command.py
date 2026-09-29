from _pydevd_bundle.pydevd_constants import (
    DebugInfoHolder,
    get_global_debugger,
    GetGlobalDebugger,
    set_global_debugger,
)  # Keep for backward compatibility @UnusedImport
from _pydevd_bundle.pydevd_utils import quote_smart as quote, to_string
from _pydevd_bundle.pydevd_comm_constants import ID_TO_MEANING, CMD_EXIT
from _pydevd_bundle.pydevd_constants import (
    DAP_MAX_BODY_SIZE,
    HTTP_PROTOCOL,
    HTTP_JSON_PROTOCOL,
    get_protocol,
    IS_JYTHON,
    ForkSafeLock,
)
import json
from _pydev_bundle import pydev_log


def _replacement_for_too_large(as_dict, size):
    """JetBrains extension (PY-92476): returns what to send in place of a message that does not fit one
    DAP body, or None when there is nothing smaller to send.
    """

    reason = "The debugger produced %s bytes for this, over the %s that one message carries." % (size, DAP_MAX_BODY_SIZE)
    if as_dict.get("type") != "response":
        pydev_log.info("Dropping a %s of %s bytes: %s", as_dict.get("type"), size, reason)
        return None

    pydev_log.info("Replacing a response of %s bytes with a failure: %s", size, reason)
    return {
        "pydevd_cmd_id": as_dict.get("pydevd_cmd_id"),
        "seq": as_dict.get("seq"),
        "type": "response",
        "request_seq": as_dict.get("request_seq"),
        "command": as_dict.get("command"),
        "success": False,
        "message": reason,
    }


class _BaseNetCommand(object):
    # Command id. Should be set in instance.
    id = -1

    # Dict representation of the command to be set in instance. Only set for json commands.
    as_dict = None

    def send(self, *args, **kwargs):
        pass

    def call_after_send(self, callback):
        pass


class _NullNetCommand(_BaseNetCommand):
    pass


class _NullExitCommand(_NullNetCommand):
    id = CMD_EXIT


# Constant meant to be passed to the writer when the command is meant to be ignored.
NULL_NET_COMMAND = _NullNetCommand()

# Exit command -- only internal (we don't want/need to send this to the IDE).
NULL_EXIT_COMMAND = _NullExitCommand()


class NetCommand(_BaseNetCommand):
    """
    Commands received/sent over the network.

    Command can represent command received from the debugger,
    or one to be sent by daemon.
    """

    next_seq = 0  # sequence numbers

    _showing_debug_info = 0
    _show_debug_info_lock = ForkSafeLock(rlock=True)

    _after_send = None

    def __init__(self, cmd_id, seq, text, is_json=False):
        """
        If sequence is 0, new sequence will be generated (otherwise, this was the response
        to a command from the client).
        """
        protocol = get_protocol()
        self.id = cmd_id
        if seq == 0:
            NetCommand.next_seq += 2
            seq = NetCommand.next_seq

        self.seq = seq

        if is_json:
            if hasattr(text, "to_dict"):
                as_dict = text.to_dict(update_ids_to_dap=True)
            else:
                assert isinstance(text, dict)
                as_dict = text
            as_dict["pydevd_cmd_id"] = cmd_id
            as_dict["seq"] = seq
            self.as_dict = as_dict
            try:
                text = json.dumps(as_dict)
            except TypeError:
                text = json.dumps(as_dict, default=str)

            # JetBrains extension (PY-92476): json.dumps escapes to ASCII, so the length of the text is
            # the length of the body in bytes, and whether it fits is known before anything is sent.
            if len(text) > DAP_MAX_BODY_SIZE:
                as_dict = _replacement_for_too_large(as_dict, len(text))
                text = "" if as_dict is None else json.dumps(as_dict)

            self.as_dict = as_dict

        assert isinstance(text, str)

        if DebugInfoHolder.DEBUG_TRACE_LEVEL >= 1:
            self._show_debug_info(cmd_id, seq, text)

        if is_json:
            msg = text
        else:
            if protocol not in (HTTP_PROTOCOL, HTTP_JSON_PROTOCOL):
                encoded = quote(to_string(text), '/<>_=" \t')
                msg = "%s\t%s\t%s\n" % (cmd_id, seq, encoded)

            else:
                msg = "%s\t%s\t%s" % (cmd_id, seq, text)

        if isinstance(msg, str):
            msg = msg.encode("utf-8")

        assert isinstance(msg, bytes)
        as_bytes = msg
        self._as_bytes = as_bytes

    def send(self, sock):
        as_bytes = self._as_bytes
        if not as_bytes:
            # JetBrains extension (PY-92476): a message that did not fit one DAP body and had no smaller
            # form was dropped when it was built. Sending an empty body instead would desynchronise the
            # reader, which is the failure this is here to avoid.
            return
        try:
            if get_protocol() in (HTTP_PROTOCOL, HTTP_JSON_PROTOCOL):
                sock.sendall(("Content-Length: %s\r\n\r\n" % len(as_bytes)).encode("ascii"))
            sock.sendall(as_bytes)
            if self._after_send:
                for method in self._after_send:
                    method(sock)
        except:
            if IS_JYTHON:
                # Ignore errors in sock.sendall in Jython (seems to be common for Jython to
                # give spurious exceptions at interpreter shutdown here).
                pass
            else:
                raise

    def call_after_send(self, callback):
        if not self._after_send:
            self._after_send = [callback]
        else:
            self._after_send.append(callback)

    @classmethod
    def _show_debug_info(cls, cmd_id, seq, text):
        with cls._show_debug_info_lock:
            # Only one thread each time (rlock).
            if cls._showing_debug_info:
                # avoid recursing in the same thread (just printing could create
                # a new command when redirecting output).
                return

            cls._showing_debug_info += 1
            try:
                out_message = "sending cmd (%s) --> " % (get_protocol(),)
                out_message += "%20s" % ID_TO_MEANING.get(str(cmd_id), "UNKNOWN")
                out_message += " "
                out_message += text.replace("\n", " ")
                try:
                    pydev_log.critical("%s\n", out_message)
                except:
                    pass
            finally:
                cls._showing_debug_info -= 1
