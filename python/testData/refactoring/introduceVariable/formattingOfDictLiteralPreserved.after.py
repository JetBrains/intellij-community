a = {
    "name": "Bob Rodgers",
    "id": hub_id,
    "login": "bob",
    "profile": {
        "email": {
            "email": "bob@example.com",
            "verified": True
        },
    },
}
self.oauth_login(target_url, a)