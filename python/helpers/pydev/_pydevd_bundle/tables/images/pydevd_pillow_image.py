#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import io
import PIL
import uuid
from _pydevd_bundle.tables.images.pydevd_image_loader import IMAGE_DATA_STORAGE
from _pydevd_bundle.tables.images.pydevd_image_loader import DEFAULT_IMAGE_FORMAT

def create_image(pillow_image):
    # type: (PIL.Image.Image) -> str
    try:
        bytes_buffer = io.BytesIO()
        try:
            image_format = pillow_image.format if pillow_image.format else DEFAULT_IMAGE_FORMAT
            pillow_image.save(bytes_buffer, format=image_format)
            bytes_data = bytes_buffer.getvalue()
            image_id = str(uuid.uuid4())
            IMAGE_DATA_STORAGE[image_id] = bytes_data
            return "{};{}".format(image_id, len(bytes_data))
        finally:
            bytes_buffer.close()
    except Exception as e:
        return "Error: {}".format(e)
