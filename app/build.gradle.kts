name: Build and Test

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]
  workflow_dispatch:

# Ensure that only the latest commit is tested if pushing rapidly
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build_test_lint:
    name: "Build, Test, and Lint"
    runs-on: ubuntu-latest
    timeout-minutes: 60
    permissions:
      contents: write # Needed for git-auto-commit

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
        # Add cache-encryption-key if you use cache
        # with:
        #   cache-encryption-key: ${{ secrets.GRADLE_ENCRYPTION_KEY }}

      - name: Decode google-services.json
        env:
          DEBUG_GOOGLE_SERVICES_JSON_BASE64: ${{ secrets.DEBUG_GOOGLE_SERVICES_JSON_BASE64 }}
        run: |
          if [ -z "$DEBUG_GOOGLE_SERVICES_JSON_BASE64" ]; then
            echo "DEBUG_GOOGLE_SERVICES_JSON not set, using test file"
            cp test-google-services.json app/google-services.json
          else
            echo "Decoding DEBUG_GOOGLE_SERVICES_JSON"
            echo $DEBUG_GOOGLE_SERVICES_JSON_BASE64 | base64 --decode > app/google-services.json
          fi

      - name: Apply Spotless
        run: ./gradlew spotlessApply

      - name: Commit Spotless changes
        uses: stefanzweifel/git-auto-commit-action@v5
        with:
          commit_message: 🤖 Apply Spotless formatting
          file_pattern: '**/*.kt **/*.kts **/*.java **/*.xml'

      - name: Build debug APK
        run: ./gradlew assembleDebug --no-daemon --stacktrace

      - name: Run local unit tests
        run: ./gradlew testDebugUnitTest --no-daemon --stacktrace

      - name: Check lint
        run: ./gradlew lintDebug --no-daemon --stacktrace

      - name: Upload build outputs (APKs)
        if: ${{ !cancelled() }}
        uses: actions/upload-artifact@v4
        with:
          name: APKs
          path: '**/build/outputs/apk/debug/*.apk'

      - name: Upload JVM local test results
        if: ${{ !cancelled() }}
        uses: actions/upload-artifact@v4
        with:
          name: local-test-results
          path: '**/build/test-results/test
