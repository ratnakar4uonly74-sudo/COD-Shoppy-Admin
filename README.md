# COD Shoppy Admin — Android App

A native Android admin app for the existing Cash on Delivery Shoppy Firebase project.

## What it does

- Google sign-in using Firebase Authentication
- Allows only `cashondeliveryshoppy@gmail.com` inside the app UI
- Add products with a photo selected from the phone Gallery
- Uses the **same Firestore `products` collection and fields** as the current customer website:
  - `name`, `price`, `description`, `image`, `active`, `createdAt`
- Product photos are resized/compressed and stored as a JPEG data URL in Firestore, matching the current website's existing product format.
- View orders and update statuses
- Save courier company + tracking ID and mark an order as shipped

## Before building: connect Firebase

1. Open Firebase Console: https://console.firebase.google.com/
2. Select project: `cashondeliveryshoppy-14861`
3. Open Project settings (gear icon) → General → Your apps.
4. Add an Android app if one does not already exist.
5. Android package name must be exactly:
   `com.cashondeliveryshoppy.admin`
6. Download `google-services.json`.
7. Put that file inside this repository's `app/` folder (same level as `app/build.gradle.kts`).
8. In Firebase Authentication → Sign-in method, enable **Google**.
9. In GitHub, after the first workflow run, open the **Build debug APK** job log and find the line printed under **Create debug signing key if missing**. Copy the SHA-1 value.
10. Add that SHA-1 in Firebase Console → Project settings → Your apps → Android app. Then download a fresh `google-services.json` and replace `app/google-services.json` in the repository.
11. Run the GitHub workflow again. Download the APK artifact from this second run and install it. The workflow caches the same debug signing key, so future APK updates from this repository keep the same app signature.
12. Ensure Firestore is enabled and the existing rules permit the admin account to read/write `products` and read/update `orders`.

> Important: Google sign-in on Android requires the correct Android app registration, package name, SHA-1 certificate and the generated `default_web_client_id` from `google-services.json`. If any one is missing or incorrect, sign-in will fail.

## Build the APK on GitHub

1. Create a new GitHub repository.
2. Upload all files and folders from this project.
3. Add `app/google-services.json` to the repository.
4. Open the repository's **Actions** tab.
5. Select **Build COD Shoppy Admin APK** and click **Run workflow** (or push to main).
6. When it finishes, open the workflow run → Artifacts → download `COD-Shoppy-Admin-APK`.
7. Extract the downloaded ZIP; it contains `app-debug.apk`. Transfer it to your Android phone and install it.

## Firebase data compatibility

The existing customer website reads product image from `products.image` and product details from the same fields listed above. This app deliberately writes that same format so a new product uploaded here appears on the existing website.

## Notes

- This repository does not include `google-services.json`; download it from your own Firebase project. Do not share it publicly outside your own private repository.
- Firebase Firestore Security Rules remain the real security boundary. The email check in the app is only a user-interface check and does not replace Firestore rules.
- The app does not change the customer website or existing Firestore documents.
- This initial version writes compressed image data into Firestore to match the current website. Firestore documents have a 1 MiB maximum; the app compresses images to reduce size. If an image is still too large, use a smaller photo.
- This is a debug APK for initial testing. A signed release APK can be configured after the app is tested.
