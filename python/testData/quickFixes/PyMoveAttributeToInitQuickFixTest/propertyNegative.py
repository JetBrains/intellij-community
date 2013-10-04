class FavouriteManager(object):
    """Favourite manager"""

    def __init__(self, session):
        self._session = session

    @property
    def _favourite_ids(self):
        """Get favourites"""
        try:
            return map(int, self._session.get('favourite', '').split(','))
        except ValueError:
            return []

    @_favourite_ids.setter
    def _favourite_ids(self, ids):
        """Set favourites ids"""
        self._session['favourite'] = ','.join(set(ids))

    def add(self, estate):
        """Add estate to favourite"""
        ids = self._favourite_ids
        ids.append(estate.id)
        self._favourite_ids = ids