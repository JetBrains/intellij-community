#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import numpy as np
from _pydevd_bundle.tables.images.pydevd_image_loader import (save_image_to_storage, GRAYSCALE_MODE, RGB_MODE, RGBA_MODE)

MAX_PIXELS = 144_000_000

def create_image(arr):
    # type: (np.ndarray) -> str
    try:
        from PIL import Image

        data_type = arr.dtype.name
        arr_to_convert = arr

        arr_to_convert = np.where(arr_to_convert == None, 0, arr_to_convert)
        arr_to_convert = np.nan_to_num(arr_to_convert, nan=0)

        if np.iscomplexobj(arr_to_convert) or np.issubdtype(arr_to_convert.dtype, np.timedelta64):
            raise ValueError("Only non-complex numeric array types are supported.")

        if arr_to_convert.ndim == 1:
            arr_to_convert = np.expand_dims(arr_to_convert, axis=0)
        elif arr_to_convert.ndim == 3 and arr_to_convert.shape[2] == 1:
            arr_to_convert = arr_to_convert[:, :, 0]

        h, w = arr_to_convert.shape[:2]
        channels = arr_to_convert.shape[2] if arr_to_convert.ndim == 3 else 1
        total_pixels = h * w * channels
        if total_pixels > MAX_PIXELS:
            scale = (MAX_PIXELS / total_pixels) ** 0.5
            new_h, new_w = max(1, int(h * scale)), max(1, int(w * scale))
            arr_to_convert = average_pooling(arr_to_convert, new_h, new_w)

        arr_min, arr_max = arr_to_convert.min(), arr_to_convert.max()
        is_float = np.issubdtype(arr_to_convert.dtype, np.floating)
        is_bool = np.issubdtype(arr_to_convert.dtype, np.bool_)

        if (is_float or is_bool) and 0 <= arr_min <= 1 and 0 <= arr_max <= 1: # bool and float in [0; 1]
            arr_to_convert = (arr_to_convert * 255).astype(np.uint8)
        elif arr_min != arr_max and (arr_min < 0 or arr_max > 255):
            arr_to_convert = ((arr_to_convert - arr_min) * 255 / (arr_max - arr_min)).astype(np.uint8) # other values out of [0; 255]
        elif arr_min == arr_max and (arr_min < 0 or arr_max > 255):
            arr_to_convert = (np.ones_like(arr_to_convert) * 127).astype(np.uint8)
        else: # values in [0; 255]
            arr_to_convert = arr_to_convert.astype(np.uint8)

        if arr_to_convert.ndim == 2:
            mode = GRAYSCALE_MODE
        elif arr_to_convert.ndim == 3 and arr_to_convert.shape[2] == 4:
            mode = RGBA_MODE
        else:
            mode = RGB_MODE

        return save_image_to_storage(Image.fromarray(arr_to_convert, mode=mode), data_type=data_type)

    except ImportError:
        return "Error: Pillow library is not installed."
    except (TypeError, ValueError):
        return "Error: Only non-complex numeric array types are supported."
    except Exception as e:
        return "Error: {}".format(e)


def average_pooling(arr, target_h, target_w):
    # type: (np.ndarray, int, int) -> np.ndarray
    h, w = arr.shape[:2]
    factor_h, factor_w = int(h / target_h), int(w / target_w)
    arr_cropped = arr[:target_h * factor_h, :target_w * factor_w]

    if arr.ndim == 2:
        reshaped = arr_cropped.reshape(target_h, factor_h, target_w, factor_w)
    elif arr.ndim == 3:
        reshaped = arr_cropped.reshape(target_h, factor_h, target_w, factor_w, arr.shape[2])
    return reshaped.mean(axis=(1, 3))
