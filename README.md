# 🗺️ GPS Map Camera

**GPS Map Camera** is a modern Android application that allows users to capture photos and automatically stamp them with highly accurate, customizable location banners. It is perfect for travelers, real estate agents, civil engineers, and anyone who needs verifiable proof of when and where a photo was taken.

---

## ✨ Features

- **Live Location Stamping**: Automatically fetches and embeds your Latitude, Longitude, and formatted street Address onto your photos.
- **Satellite Map Thumbnails**: Integrates a mini Google Maps satellite thumbnail directly into the watermark.
- **Editable Metadata**: Manually adjust the GPS coordinates or the Date & Time before saving. The app automatically reverse-geocodes your new coordinates to update the address on the fly!
- **Customizable Banner Layout**: Use intuitive sliders to adjust the Left, Right, and Bottom margins of the banner so it never covers up the most important parts of your photo.
- **High-Quality Export**: Stamps the banner using native Android Canvas rendering to preserve the full resolution of the captured image.
- **Native Android Pickers**: Uses native Android Date and Time picker dialogs for a seamless user experience.

---

## 🛠️ Tech Stack

- **Kotlin**: Built entirely in Kotlin for a clean, modern, and type-safe codebase.
- **Jetpack Compose**: The entire UI (including the live camera preview and edit controls) is built using Android's modern declarative UI toolkit.
- **CameraX**: For a reliable, lifecycle-aware camera experience.
- **FusedLocationProviderClient (Google Play Services)**: For highly accurate and efficient location tracking.
- **Geocoder API**: For reverse-geocoding latitude and longitude into human-readable street addresses and country flags.
- **Coroutines & Dispatchers**: For smooth, non-blocking background tasks (like downloading map tiles and rendering the final high-resolution bitmap).

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio** (Giraffe or newer recommended)
- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 34 (Android 14)

### Installation
1. Clone this repository:
   ```bash
   git clone https://github.com/yourusername/cameraGPS.git
   ```
2. Open the project in Android Studio.
3. Sync the project with Gradle files.
4. Run the app on a physical Android device (Camera and GPS features require physical hardware to function correctly).

### Permissions Required
The app will request the following permissions at runtime:
- `CAMERA`: To capture photos.
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`: To fetch the live GPS coordinates.

---

## 📂 Project Structure

- `MainActivity.kt`: The main entry point, handles permissions, state management, and the Jetpack Compose UI.
- `LocationHelper.kt`: Manages the FusedLocationProvider, Geocoder logic, and location data formatting.
- `LocationBannerOverlay.kt`: The Jetpack Compose UI component that overlays the live location data on top of the camera preview.
- `ImageStamper.kt`: Uses the Android Canvas API to physically draw the location banner onto the final high-resolution Bitmap before saving it to the gallery.

---

## 📝 License
This project is licensed under the [MIT License](LICENSE).
