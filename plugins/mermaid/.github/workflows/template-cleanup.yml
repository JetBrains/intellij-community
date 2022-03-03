# GitHub Actions Workflow responsible for cleaning up the IntelliJ Platform Plugin Template repository from
# the template-specific files and configurations. This workflow is supposed to be triggered automatically
# when a new template-based repository has been created.

name: Template Cleanup
on:
  push:
    branches:
      - main

jobs:

  # Run cleaning process only if workflow is triggered by the non-JetBrains/intellij-platform-plugin-template repository.
  template-cleanup:
    name: Template Cleanup
    runs-on: ubuntu-latest
    if: github.event.repository.name != 'intellij-platform-plugin-template'
    steps:

      # Check out current repository
      - name: Fetch Sources
        uses: actions/checkout@v2.4.0

      # Cleanup project
      - name: Cleanup
        run: |
          export LC_CTYPE=C
          export LANG=C

          # Prepare variables
          NAME="${GITHUB_REPOSITORY##*/}"
          ACTOR=$(echo $GITHUB_ACTOR | tr '[:upper:]' '[:lower:]')
          SAFE_NAME=$(echo $NAME | sed 's/[^a-zA-Z0-9]//g' | tr '[:upper:]' '[:lower:]')
          SAFE_ACTOR=$(echo $ACTOR | sed 's/[^a-zA-Z0-9]//g' | tr '[:upper:]' '[:lower:]')
          GROUP="com.github.$SAFE_ACTOR.$SAFE_NAME"

          # Replace placeholders in the template-cleanup files
          sed -i "s/%NAME%/$NAME/g" .github/template-cleanup/*
          sed -i "s/%REPOSITORY%/${GITHUB_REPOSITORY/\//\\/}/g" .github/template-cleanup/*
          sed -i "s/%GROUP%/$GROUP/g" .github/template-cleanup/*

          # Replace template package name in project files with $GROUP
          find src -type f -exec sed -i "s/org.jetbrains.plugins.template/$GROUP/g" {} +
          find src -type f -exec sed -i "s/Template/$NAME/g" {} +
          find src -type f -exec sed -i "s/JetBrains/$ACTOR/g" {} +

          # Move content
          mkdir -p src/main/kotlin/${GROUP//.//}
          mkdir -p src/test/kotlin/${GROUP//.//}
          cp -R .github/template-cleanup/* .
          cp -R src/main/kotlin/org/jetbrains/plugins/template/* src/main/kotlin/${GROUP//.//}/
          cp -R src/test/kotlin/org/jetbrains/plugins/template/* src/test/kotlin/${GROUP//.//}/

          # Cleanup
          rm -rf \
            .github/ISSUE_TEMPLATE \
            .github/readme \
            .github/template-cleanup \
            .github/workflows/template-cleanup.yml \
            .idea/icon.png \
            src/main/kotlin/org \
            src/test/kotlin/org \
            CODE_OF_CONDUCT.md \
            LICENSE

      # Commit modified files
      - name: Commit files
        run: |
          git config --local user.email "action@github.com"
          git config --local user.name "GitHub Action"
          git add .
          git commit -m "Template cleanup"

      # Push changes
      - name: Push changes
        uses: ad-m/github-push-action@master
        with:
          branch: main
          github_token: ${{ secrets.GITHUB_TOKEN }}
