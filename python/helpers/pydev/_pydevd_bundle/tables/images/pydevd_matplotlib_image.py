#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import io
import matplotlib
import uuid
from _pydevd_bundle.tables.images.pydevd_image_loader import IMAGE_DATA_STORAGE
from _pydevd_bundle.tables.images.pydevd_image_loader import DEFAULT_IMAGE_FORMAT

def create_image(matplotlib_figure):
    # type: (matplotlib.figure.Figure) -> str
    try:
        bytes_buffer = io.BytesIO()
        try:
            matplotlib_figure.savefig(bytes_buffer, format=DEFAULT_IMAGE_FORMAT)
            bytes_buffer.seek(0)
            bytes_data = bytes_buffer.getvalue()
            image_id = str(uuid.uuid4())
            IMAGE_DATA_STORAGE[image_id] = bytes_data
            return "{}".format(image_id)
        finally:
            bytes_buffer.close()
    except Exception as e:
        return "Error: {}".format(e)
