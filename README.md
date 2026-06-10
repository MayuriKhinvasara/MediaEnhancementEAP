# Automated Latency Benchmark Tests

This project contains an instrumented Android benchmark test suite (`EnhancementViewModelBenchmarkTest`) designed to measure image enhancement latency differences across different API modes:
- **Tonemap Only**
- **Tonemap + Image Upscale**
- **Tonemap + Video Upscale**

The benchmarks output a clean markdown report compiling latency timings, output sizes, key findings, and error messages for each run.

---

## 1. Input Folder and Image Setup

The benchmark test reads input image files directly from the device's public `Pictures` directory. 

Before running the test suite, you must structure and push your benchmark images to the device using the following setup:

### Directory Structure
Create a directory named `Enhance` in your device's `Pictures` folder, with separate resolution subfolders:

```text
/sdcard/Pictures/Enhance/
├── 1920x1080/
│   ├── video_015_1920x1080.png
│   └── video_016_1920x1080.png
└── 1280x720/
    ├── video_015_1280x720.png
    └── video_016_1280x720.png
```

### Pushing Images via ADB
You can use `adb push` to copy files from your host machine onto the device:

```bash
# Push 1080p images
adb push path/to/local/1920x1080/ /sdcard/Pictures/Enhance/1920x1080/

# Push 720p images
adb push path/to/local/1280x720/ /sdcard/Pictures/Enhance/1280x720/
```

---

## 2. Running the Benchmark

You can run the tests from Android Studio by right-clicking on `EnhancementViewModelBenchmarkTest` and choosing **Run**, or via the command line:

```bash
./gradlew app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.android.imagesuperresolution.EnhancementViewModelBenchmarkTest
```

> [!NOTE]
> The benchmark test script automatically grants `MANAGE_EXTERNAL_STORAGE` to the app shell at runtime to read files from `/sdcard/Pictures/` and write report documents.

---

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
