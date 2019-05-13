import pytest

@pytest.mark.parametrize('username,url', [
    (None, 'https://facebook.com/'),
    (None, 'https://facebook.com/share.php?http://foo.com/'),
    (None, 'https://facebook.com/home.php'),
    ('username', 'https://facebook.com/username'),
    ('username', 'https://facebook.com/username/app_123'),
])
def test_get_facebook_username(url, username):
    pass