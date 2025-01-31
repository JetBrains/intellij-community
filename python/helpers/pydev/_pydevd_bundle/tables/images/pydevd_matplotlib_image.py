#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import base64
import io
import matplotlib

DEFAULT_IMAGE_FORMAT = 'PNG'
DEFAULT_ENCODING = 'utf-8'


def get_bytes(matplotlib_figure):
    # type: (matplotlib.figure.Figure) -> str
    try:
        bytes_buffer = io.BytesIO()
        matplotlib_figure.savefig(bytes_buffer, format=DEFAULT_IMAGE_FORMAT)
        bytes_buffer.seek(0)
        return base64.b64encode(bytes_buffer.getvalue()).decode(DEFAULT_ENCODING)
    except Exception as e:
        return "Error: {}".format(e)