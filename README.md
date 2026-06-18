# Automated Latency Benchmark Tests

This project contains an instrumented Android benchmark test suite (`EnhancementViewModelBenchmarkTest`) designed to measure image enhancement latency differences across different API modes:
- **Tonemap Only**
- **Tonemap + Image Upscale**
- **Tonemap + Video Upscale**
- **Image Upscale Only**
- **Video Upscale Only**

The benchmarks output a clean markdown report compiling latency timings, output sizes, key findings, and error messages for each run.

---

## 1. Required Setup & Developer Action Items

Before executing the benchmark suite, you **MUST** complete the following setup actions:

### Action Item 1: Push Test Images to the Device
The benchmark test reads input image files directly from a flat `Enhance` folder in the device's public `Pictures` directory.
1. Create the `Enhance` folder inside the device's `Pictures` directory.
2. Push all local benchmark images directly into it:
   ```bash
   adb push path/to/local/images/. /sdcard/Pictures/Enhance/
   ```
   *Note: Do not create any subfolders (like 1080p/720p) inside the `Enhance` directory.*

### Action Item 2: Launch the App Manually Once (Play Services Prerequisite)
You **must run the application manually on the device at least once** before triggering the automated benchmarks.
* **Why**: The SDK dynamically downloads machine learning models and library dependencies via Google Play Services on first launch.
* **Impact**: If you skip this, the benchmark session will fail to initialize and the tests will crash with module-loading errors.

### Action Item 3: Grant Storage Permission
To allow the test app to read your images from public directories and save reports, grant full storage access:
```bash
adb shell appops set com.android.imagesuperresolution MANAGE_EXTERNAL_STORAGE allow
```

---

## 2. Running the Benchmark

You can execute the benchmark test suite either directly from Android Studio or via the command line:

### Option A: Running from Android Studio (Recommended)
1. Open the project in Android Studio.
2. In the project browser, navigate to:
   `app/src/androidTest/java/com/android/imagesuperresolution/EnhancementViewModelBenchmarkTest.kt`
3. Open the file, right-click on the class definition (`EnhancementViewModelBenchmarkTest`), and click **Run 'EnhancementViewModel...'** (or click the green play icon next to the class name).

### Option B: Running via Command Line
Execute the following Gradle command in the project root:
```bash
./gradlew app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.android.imagesuperresolution.EnhancementViewModelBenchmarkTest
```

## 3. Retrieving the Markdown Report

When the benchmark run finishes, it compiles the results and saves a Markdown report named after your device model:
`Enhancement_ViewModel_Benchmark_[DEVICE_MODEL].md`

The file is written to the app's external files directory:
`/sdcard/Android/data/com.android.imagesuperresolution/files/Documents/`

You can pull the generated report onto your computer using `adb pull`:

```bash
# Example pulling a Pixel 10 Pro report
adb pull /sdcard/Android/data/com.android.imagesuperresolution/files/Documents/Enhancement_ViewModel_Benchmark_Pixel_10_Pro.md ./
```
