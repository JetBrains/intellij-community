#  Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
from IPython.core.displayhook import DisplayHook
from IPython.core.displaypub import DisplayPublisher


# TODO: Think about rich output and display pictures
class PyDevDebugDisplayHook(DisplayHook):
    def write_format_data(self, format_dict, *args, **kwargs):
        print_text_from_dict(format_dict)


class PyDevDebugDisplayPub(DisplayPublisher):
    def publish(self, data, *args, **kwargs):
        print_text_from_dict(data)


def print_text_from_dict(dict):
    if 'text/plain' in dict:
        text = dict['text/plain']
        if '\n' in text:
            text = '\n' + text
        print(text)