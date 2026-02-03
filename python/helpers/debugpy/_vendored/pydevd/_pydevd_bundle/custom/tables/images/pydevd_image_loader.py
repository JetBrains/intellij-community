#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import base64
import io
import uuid

IMAGE_DATA_STORAGE = {}
DEFAULT_IMAGE_FORMAT = 'PNG'
DEFAULT_ENCODING = 'utf-8'
GRAYSCALE_MODE = 'L'
RGB_MODE = 'RGB'
RGBA_MODE = 'RGBA'
CHUNK_SIZE = 8192

def load_image_chunk(offset, image_id):
    # type: (int, str) -> str
    try:
        bytes_data = IMAGE_DATA_STORAGE.get(image_id)
        if bytes_data is None:
            return "Error: No image data found."
        chunk = bytes_data[offset:offset + CHUNK_SIZE]
        next_offset = offset + CHUNK_SIZE
        if next_offset >= len(bytes_data):
            next_offset = -1
            IMAGE_DATA_STORAGE.pop(image_id, None)
        chunk_bytes = base64.b64encode(chunk)
        if not isinstance(chunk_bytes, str):
            chunk_bytes = chunk_bytes.decode(DEFAULT_ENCODING)
        return "{};{}".format(chunk_bytes, next_offset)
    except ValueError:
        return "Error: Invalid offset format."
    except Exception as e:
        return "Error: {}".format(e)


def save_image_to_storage(image_data, data_type=None, format=DEFAULT_IMAGE_FORMAT, save_func=None):
    # type: (any, str, str, callable) -> str
    try:
        bytes_buffer = io.BytesIO()
        try:
            if save_func:
                save_func(bytes_buffer, format)
            else:
                image_data.save(bytes_buffer, format=format)
            bytes_buffer.seek(0)
            bytes_data = bytes_buffer.getvalue()
            image_id = str(uuid.uuid4())
            IMAGE_DATA_STORAGE[image_id] = bytes_data
            if data_type is None:
                data_type = "None"
            return "{};{};{}".format(image_id, len(bytes_data), data_type)
        finally:
            bytes_buffer.close()
    except Exception as e:
        return "Error: {}".format(e)
