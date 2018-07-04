try:
    __import__('pkg_resources').declare_namespace(__name__)
except:
    import pkgutil
    __path__ = pkgutil.extend_path(__path__, __name__)
