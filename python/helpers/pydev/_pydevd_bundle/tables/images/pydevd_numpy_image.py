#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import numpy as np
import io
import base64


DEFAULT_IMAGE_FORMAT = 'PNG'
DEFAULT_ENCODING = 'utf-8'
GRAYSCALE_MODE = 'L'
RGB_MODE = 'RGB'
RGBA_MODE = 'RGBA'


def get_bytes(arr):
    # type: (np.ndarray) -> str
    try:
        from PIL import Image

        arr_to_convert = arr
        arr_to_convert = np.where(arr_to_convert == None, 0, arr_to_convert)
        arr_to_convert = np.nan_to_num(arr_to_convert, nan=0)

        if np.iscomplexobj(arr_to_convert) or np.issubdtype(arr_to_convert.dtype, np.timedelta64):
            raise ValueError("Only non-complex numeric array types are supported.")

        if arr_to_convert.ndim == 1:
            arr_to_convert = np.expand_dims(arr_to_convert, axis=0)

        arr_min, arr_max = np.min(arr_to_convert), np.max(arr_to_convert)
        if arr_min == arr_max:  # handle constant values
            arr_to_convert = np.full_like(arr_to_convert, 127, dtype=np.uint8)
        else:
            arr_to_convert = ((arr_to_convert - arr_min) / (arr_max - arr_min) * 255).astype(np.uint8)

        arr_to_convert_ndim = arr_to_convert.ndim
        if arr_to_convert_ndim == 2:
            mode = GRAYSCALE_MODE
        elif arr_to_convert_ndim == 3 and arr_to_convert.shape[2] == 4:
            mode = RGBA_MODE
        else:
            mode = RGB_MODE
        bytes_buffer = io.BytesIO()
        image = Image.fromarray(arr_to_convert, mode=mode)
        image.save(bytes_buffer, format=DEFAULT_IMAGE_FORMAT)
        return base64.b64encode(bytes_buffer.getvalue()).decode(DEFAULT_ENCODING)
    except ImportError:
        return "Error: Pillow library is not installed."
    except (TypeError, ValueError):
        return "Error: Only non-complex numeric array types are supported."
    except Exception as e:
        return "Error: {}".format(e)