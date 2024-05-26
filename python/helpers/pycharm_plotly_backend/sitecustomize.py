import os
import sys
import traceback

SHOW_DEBUG_INFO = os.getenv('PYCHARM_DEBUG', 'False').lower() in ['true', '1']


def debug(message):
    if SHOW_DEBUG_INFO:
        sys.stderr.write(message)
        sys.stderr.write("\n")


def init_plotly_render():
    from datalore.display import display, debug

    is_python_3_or_higher = sys.version_info[0] >= 3
    if not is_python_3_or_higher:
        debug("Pycharm Plotly backend is not supported for Python 2")
        return

    # noinspection PyProtectedMember
    from plotly.io._base_renderers import ExternalRenderer
    # noinspection PyProtectedMember
    from plotly.io._renderers import renderers

    class DisplayDataObject:
        def __init__(self, html_string: str):
            self.html_string = html_string

        def _repr_display_(self):
            body = {
                'html_string': self.html_string
            }
            return 'pycharm-plotly-image', body

    class PycharmRenderer(ExternalRenderer):
        def __init__(
                self,
                config=None,
                auto_play=False,
                using=None,
                new=0,
                autoraise=True,
                post_script=None,
                animation_opts=None,
        ):
            self.config = config
            self.auto_play = auto_play
            self.using = using
            self.new = new
            self.autoraise = autoraise
            self.post_script = post_script
            self.animation_opts = animation_opts

        def render(self, fig_dict):
            from plotly.io import to_html

            html = to_html(
                fig_dict,
                config=self.config,
                auto_play=self.auto_play,
                include_plotlyjs=True,
                include_mathjax="cdn",
                post_script=self.post_script,
                full_html=True,
                animation_opts=self.animation_opts,
                default_width="100%",
                default_height="100%",
                validate=False,
            )
            display(DisplayDataObject(html))

    debug("Set plotly default render")
    renderers._default_renderers = [PycharmRenderer()]


debug("Executing PyCharm's Ploly `sitecustomize`")
modules_list = []

try:
    # We want to import users sitecustomize.py file if any
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
                # return our module if we failed to find any other sitecustomize
                # to prevent KeyError importing 'site.py'
                sys.modules[sitecustomize] = pycharm_sitecustomize_module

        sys.path.append(parent_dir)

    # Use matplotlib backend from pycharm
    modules_list = list(sys.modules.keys())

    init_plotly_render()
    debug("Custom plotly backend was set for Plots tool window")
except:
    # fallback in case matplotlib is not loaded correctly
    debug("Cannot init plotly backend")
    if SHOW_DEBUG_INFO:
        traceback.print_exc()

    keys = list(sys.modules.keys())
    if modules_list:
        for key in keys:
            if key not in modules_list:
                sys.modules.pop(key)