# MediaEnhancementEAP: AI Image Enhancement Sample App

This repository contains a standalone Android sample application demonstrating the use of the **Google Play Services Media Effect Enhancement APIs** to perform on-device AI-powered image processing.

The application allows users to load an image and apply various enhancement effects, showcasing a side-by-side comparison of the original and enhanced results.

## Features Demonstrated
*   **On-Device AI Processing**: Uses local on-device models powered by Google Play Services.
*   **Tonemapping**: Enhances the dynamic range, colors, and contrast of the image.
*   **Deblurring & Denoising**: Reduces noise and sharpens blurry details in images.
*   **Image Upscaling (Super Resolution)**: Increases the resolution and quality of the image using AI upscaling.
*   **Jetpack Compose UI**: Built with modern Jetpack Compose, featuring a side-by-side comparison screen.

## Project Structure
*   `EnhancementUtils.kt`: A coroutine-based wrapper around the callback-based Google Play Services Enhancement APIs (session creation, bitmap processing, module installation).
*   `EnhancementViewModel.kt`: Manages the UI state, triggers module installation, manages the enhancement session lifecycle, and processes selected images.
*   `ImageComparisonScreen.kt`: The Compose-based UI displaying the original and enhanced images with controls to toggle different enhancement options.

## Setup & Getting Started

### 1. Prerequisites
*   An Android device running **Android 11 (API level 30)** or higher.
*   **Google Play Services** installed on the device.

### 2. First-Time Launch (Important)
When the app is launched for the first time, it will check if the required AI enhancement modules are installed. If not, it will automatically download them via Google Play Services.
*   **Note**: Ensure the device is connected to the internet on the first launch so that Google Play Services can download the machine learning models.

### 3. Running the App
1. Clone the repository.
2. Open the project in Android Studio.
3. Build and run the `app` configuration on your connected device.
