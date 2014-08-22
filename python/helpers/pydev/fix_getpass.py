def fixGetpass():
  import getpass
  import warnings
  fallback = getattr(getpass, 'fallback_getpass', None) # >= 2.6
  if not fallback:
      fallback = getpass.default_getpass # <= 2.5
  getpass.getpass = fallback
  if hasattr(getpass, 'GetPassWarning'):
      warnings.simplefilter("ignore", category=getpass.GetPassWarning)

