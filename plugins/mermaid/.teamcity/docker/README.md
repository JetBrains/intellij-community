# Updating images in the registry

1. Do you changes to the `Dockerfile`
2. Update version in `docker-compose.yml`
3. Run `docker-compose build --no-cache && docker-compose push` to build and push the new image to the registry
