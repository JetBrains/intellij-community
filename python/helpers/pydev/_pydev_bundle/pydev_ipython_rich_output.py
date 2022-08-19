#  Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import sys
from IPython.core.displayhook import DisplayHook
from IPython.core.displaypub import DisplayPublisher


class PyDevDisplayHook(DisplayHook):
    """ Used for execution result """
    def write_format_data(self, format_dict, *args, **kwargs):
        if not is_supported(format_dict):
            super(PyDevDisplayHook, self).write_format_data(format_dict, *args,
                                                            **kwargs)
            return
        add_new_line_to_text(format_dict)
        format_dict["execution_count"] = str(get_current_exec_count(self.shell))
        send_rich_output(format_dict)


class PyDevDisplayPub(DisplayPublisher):
    """ Used for display() function """
    def publish(self, data, *args, **kwargs):
        if not is_supported(data):
            super(PyDevDisplayPub, self).publish(data, *args, **kwargs)
            return
        add_new_line_to_text(data)
        data["execution_count"] = str(get_current_exec_count(self.shell))
        send_rich_output(data)


def get_current_exec_count(shell):
    if hasattr(shell, "pydev_curr_exec_line") and shell.pydev_curr_exec_line != 0:
        return shell.pydev_curr_exec_line
    else:
        return shell.execution_count


def add_new_line_to_text(format_dict):
    if "text/plain" in format_dict:
        text = format_dict["text/plain"]
        if len(text) > 0 and not text.endswith("\n"):
            format_dict["text/plain"] = text + "\n"


def is_supported(data):
    for type in data.keys():
        if type not in ("text/plain", "image/png", "text/html"):
            return False
        if type == "text/html":
            html = data["text/html"]
            if not is_data_frame(html):
                return False
    return True


def is_data_frame(html):
    return ("javascript" not in html) and ("dataframe" in html) and ("<table" in html)


def send_rich_output(data):
    if 'image/png' in data:
        png = data['image/png']
        if not isinstance(png, str):
            import base64
            res = base64.b64encode(png)
            data['image/png'] = res
    from _pydev_bundle.pydev_ipython_console_011 import get_client
    client = get_client()
    if client:
        client.sendRichOutput(data)
    else:
        sys.stderr.write("Client is None!\n")
        sys.stderr.flush()


def patch_stdout(pydev_shell):
    sys.stdout = PydevStdOut(sys.stdout, pydev_shell)


class PydevStdOut:
    def __init__(self, original_stdout=sys.stdout, pydev_shell=None, *args, **kwargs):
        self.encoding = sys.stdout.encoding
        self.original_stdout = original_stdout
        self.pydev_shell = pydev_shell

    def write(self, s):
        data = {'text/plain': s}
        data['execution_count'] = str(self.pydev_shell.execution_count)
        send_rich_output(data)

    def __getattr__(self, item):
        # it's called if the attribute wasn't found
        if hasattr(self.original_stdout, item):
            return getattr(self.original_stdout, item)
        raise AttributeError("%s has no attribute %s" % (self.original_stdout, item))

