#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import base64
import io
import PIL

DEFAULT_IMAGE_FORMAT = 'PNG'
DEFAULT_ENCODING = 'utf-8'
GRAYSCALE_MODE = 'L'
RGB_MODE = 'RGB'


def get_bytes(pillow_image):
    # type: (PIL.Image.Image) -> str
    try:
        bytes_buffer = io.BytesIO()
        image_format = pillow_image.format if pillow_image.format else DEFAULT_IMAGE_FORMAT
        pillow_image.save(bytes_buffer, format=image_format)
        return base64.b64encode(bytes_buffer.getvalue()).decode(DEFAULT_ENCODING)
    except Exception as e:
        return "Error: {}".format(e)