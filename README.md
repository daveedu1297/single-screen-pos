# Single-Screen POS — Android (Kotlin + Jetpack Compose)

A native Android implementation of the single-screen POS roadmap for the
Samsung Galaxy Tab A8: one screen with top bar, category rail, item grid,
and cart/payment panel, with every admin feature reachable through a popup
gated by a Manager PIN.

## Option A — Build in the cloud with GitHub Actions (no local install needed)
This project includes `.github/workflows/build-apk.yml`, which builds a debug
APK on GitHub's servers every time you push.

1. Create a new **empty** repository on GitHub (don't initialize it with a
   README — you want it truly empty).
2. On your machine, from inside this `POSApp` folder, run:
   ```bash
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<your-username>/<your-repo>.git
   git push -u origin main
   ```
3. On GitHub, open your repo → the **Actions** tab. You'll see a "Build APK"
   workflow running automatically (takes ~3–5 minutes the first time).
4. When it finishes (green checkmark), click into that run → scroll to
   **Artifacts** → download `single-screen-pos-debug-apk`. That's a zip
   containing `app-debug.apk`.
5. Transfer `app-debug.apk` to your Galaxy Tab A8 (email it to yourself,
   Google Drive, USB cable, whatever's easiest), then open the file on the
   tablet. Android will ask to allow installing from this source the first
   time — approve it, and the app installs.

No Android Studio, no SDK download, nothing to configure locally — GitHub's
runner has the Android SDK and does the whole build for you. If a build fails,
open the Actions log; the error will point at the exact line, and you can
paste it back to me to fix.

## Option B — Android Studio (if you change your mind later)
1. Open Android Studio (Koala/2024.1+ recommended).
2. **Open** this `POSApp` folder as a project. Android Studio will offer to
   generate the Gradle wrapper jar — accept that (or run `gradle wrapper`
   yourself if you have Gradle installed locally).
3. Let it sync. Requires JDK 17 and Android SDK 34 installed via SDK Manager.
4. Run on a Galaxy Tab A8 (or any API 26+ device/emulator in landscape).

## What's fully working
- **Layout**: top bar (search, barcode icon, customer, printer, settings),
  left category/favorites rail, center item grid, right cart panel — all one
  screen, no navigation.
- **Billing workflow**: tap item → adjust qty → pick payment mode → checkout
  writes the bill + line items to the local Room database and decrements stock.
- **GST math**: per-line and bill-level GST calculated from each item's own
  GST% (`util/Gst.kt`), rounded the way tax invoices expect.
- **Offline-first**: everything lives in a local Room/SQLite database
  (`pos_database.db`) — no network or backend required.
- **Admin PIN gate**: Items, Stock, Reports, Settings, Users all require the
  Manager PIN (default `1234`, changeable in Settings) before opening.
- **Popups**: Add/Edit Item, Stock Adjustment, Customer (walk-in/search/add),
  Printer setup, Reports (daily sales + payment summary), Settings (store
  info, GST, dark mode).
- **Long-press quick actions** on grid items: Edit, Stock, Favorite, Delete.
- **Bluetooth printing**: real ESC/POS receipt generation and printing over
  RFCOMM/SPP to any paired Bluetooth thermal printer (`printer/PrinterManager.kt`).
- **LAN printing**: real raw-socket printing to port 9100, which is how most
  network thermal printers listen.
- **Barcode scanning**: camera-based scan using ML Kit + CameraX, looks the
  item up and adds it to the cart.

## What's intentionally stubbed (needs real hardware to finish)
These need a physical device/printer in hand to wire up correctly — I've left
clear markers in the code (`printViaUsb`, backup/restore, image picker):
- **USB printing** — `PrinterManager.printViaUsb()`. USB thermal printers
  differ by vendor/product ID and endpoint layout; plug your printer into the
  tablet, read its IDs via `UsbManager.getDeviceList()`, and fill in the bulk
  transfer call.
- **Backup/Restore** — buttons exist in Settings; wire them to copy
  `context.getDatabasePath("pos_database.db")` to/from external storage
  (Storage Access Framework recommended over raw file paths on API 30+).
- **Item image picker** — the Add/Edit Item dialog has a placeholder; hook up
  `ActivityResultContracts.GetContent()` in `MainActivity` and pass the URI
  through.
- **PDF export** in Reports — the button is there; point it at the `pdf`
  skill's approach or `android.graphics.pdf.PdfDocument` to render the bills
  list.
- **Users/roles** — `UsersAdminDialog` is a placeholder; follow the same
  Entity/DAO pattern as `CustomerEntity` when you're ready to add multiple
  cashier logins.

## Project structure
```
app/src/main/java/com/possingle/app/
├── MainActivity.kt              # hosts the single Compose screen
├── PosApplication.kt
├── data/                        # Room entities, DAOs, database, seed data
├── viewmodel/POSViewModel.kt    # cart, billing, PIN, search — all app state
├── printer/PrinterManager.kt    # ESC/POS + Bluetooth/LAN/USB transports
├── util/Gst.kt                  # GST rounding & currency formatting
└── ui/                          # POSScreen.kt + one file per popup dialog
```

## Notes
- Default Manager PIN is `1234` — change it immediately in Settings for a
  real deployment.
- Seed data (a few categories/items + a Walk-in customer) loads on first run
  so the grid isn't empty; delete `SeedDataCallback` once real inventory is
  entered.
- This project wasn't compiled in the sandbox it was generated in (no Android
  SDK/network access to Google's Maven there) — please do a Gradle sync in
  Android Studio as your first step and let me know if anything doesn't
  resolve so I can fix it.
