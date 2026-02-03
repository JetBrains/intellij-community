import base64
import io
import os
import sys
import traceback

SHOW_DEBUG_INFO = os.getenv('PYCHARM_DEBUG', 'False').lower() in ['true', '1']

def debug(message):
    if SHOW_DEBUG_INFO:
        sys.stderr.write(message)
        sys.stderr.write("\n")

def init_altair_render():
    from datalore.display import display

    is_python_3_or_higher = sys.version_info[0] >= 3
    if not is_python_3_or_higher:
        debug("PyCharm Altair backend is not supported for Python 2")
        return
    try:
        import altair as alt
    except ImportError:
        return

    class DisplayDataObject:
        def __init__(self, html_string, image_string=None):
            # type: (str, str) -> None
            self.html_string = html_string
            self.image_string = image_string

        def _repr_display_(self):
            body = {"html_string": self.html_string}
            if self.image_string:
                body["image_base64"] = self.image_string
            return "pycharm-altair-image", body

    def pycharm_renderer(spec):
        saved_renderer = alt.renderers.active
        image_str = None
        html_str = ""
        try:
            alt.renderers.enable("png")
            png_renderer = alt.renderers.get()
            image_bytes = png_renderer(spec)[0]['image/png']
            image_str = base64.b64encode(image_bytes).decode("utf8")
        except:
            debug("Failed to render image")
        finally:
            alt.renderers.enable(saved_renderer)

        try:
            alt.renderers.enable("html")
            html_renderer = alt.renderers.get()
            html_str = html_renderer(spec)['text/html']
        except:
            debug("Failed to render HTML")
        finally:
            alt.renderers.enable(saved_renderer)
        display(DisplayDataObject(html_str, image_str))

    alt.renderers.register("browser", pycharm_renderer)
    alt.renderers.enable("browser")


debug("Executing PyCharm's Altair `sitecustomize`")
modules_list = []

try:
    sitecustomize = "sitecustomize"
    parent_dir = os.path.abspath(os.path.join(__file__, os.pardir))
    if parent_dir in sys.path:
        sys.path.remove(parent_dir)

        if sitecustomize in sys.modules:
            pycharm_sitecustomize_module = sys.modules.pop(sitecustomize)

            try:
                import sitecustomize
            except ImportError:
                debug("User doesn't have a custom `sitecustomize`")
                sys.modules[sitecustomize] = pycharm_sitecustomize_module

        sys.path.append(parent_dir)

    modules_list = list(sys.modules.keys())
    old_getfilesystemencoding = None

    if not sys.getfilesystemencoding():
        old_getfilesystemencoding = sys.getfilesystemencoding
        sys.getfilesystemencoding = lambda: "UTF-8"

    try:
        init_altair_render()
    except:
        debug("Cannot initialize Altair backend")
        if SHOW_DEBUG_INFO:
            traceback.print_exc()

    if old_getfilesystemencoding:
        sys.getfilesystemencoding = old_getfilesystemencoding
    debug("Custom Altair backend was set for the Plots tool window")
except:
    keys = list(sys.modules.keys())
    if modules_list:
        for key in keys:
            if key not in modules_list:
                sys.modules.pop(key)