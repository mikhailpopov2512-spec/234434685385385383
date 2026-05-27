# Sberbank Virtual Assistant & Mobile App

A modern, material-designed virtual financial banking assistant app built using Jetpack Compose and Kotlin, featuring customizable assistant personas (Sber, Joy, Athena) and dynamic Gemini AI integration for realistic real-time financial conversations, analytics, and transaction plans.

---

## 🚀 CI/CD Integration via GitHub Actions

This repository is fully configured for automated cloud builds and tests using **GitHub Actions**.

### What the workflow does:
1. **Triggers automatically**: Runs on every push or pull request to `main`, `master`, or `develop` branches.
2. **Prepares Environment**: Restores the environment settings (`.env`) from template files.
3. **Restores Debug Keystore**: Automatically decodes `debug.keystore.base64` into a valid local `debug.keystore` file to guarantee matched cryptographic signatures for all builds.
4. **Validates Quality**: Executes unit tests automatically to confirm functional integrity.
5. **Builds APKs**: Packages the Android project into a debug APK.
6. **Uploads Artifacts**: Publishes the compiled APK inside the GitHub Actions run summary so developers can easily install and test on physical devices or emulators.

---

## 🛠️ Local Build Instructions

Follow these simple commands to compile, test, or run your project locally:

### 1. Configure Credentials
Add your secure Gemini API Key in `/app/build.gradle.kts` configuration properties or simply define it inside a `.env` file at the root level matching this structure:
```env
GEMINI_API_KEY=YOUR_ACTUAL_KEY_HERE
```

### 2. Prepare build environment (Restore debug keystore)
Ensure that the local keystore exists by decoding the secure base64 template:
```bash
base64 --decode debug.keystore.base64 > debug.keystore
```

### 3. Clean and Build the Project
Use the gradle wrapper scripts included at the project root to start compiles:

* **Linux / macOS**:
  ```bash
  chmod +x gradlew
  ./gradlew clean assembleDebug
  ```

* **Windows**:
  ```cmd
  gradlew.bat clean assembleDebug
  ```

### 4. Run Unit Tests
To run local JVM unit tests and check logic (including any Robolectric tests):
```bash
./gradlew testDebugUnitTest
```

---

## 🎨 Major Features included:
* **Interactive Chat Experience**: Chat directly with AI personas designed with specific financial styles and customized greetings.
* **Persona Selector**: Toggle between *Sber* (professional & direct), *Joy* (active & enthusiastic), and *Athena* (calm, intellectual, analytical).
* **Mock Transactions Engine**: Dynamic balance queries, payments, and categorized budgeting.
