# TradeFlow - Native Android Trading Journal

A professional native Android app for tracking trading performance and analyzing trade statistics.

## Features

✅ **Dashboard**
- Real-time performance stats (Total P&L, Win Rate, Profit Factor, Average Return)
- Recent trades overview
- Pull-to-refresh functionality
- Quick theme toggle

✅ **Analytics**
- P&L over time line chart
- Win/Loss distribution pie chart
- Trade distribution by symbol bar chart
- Interactive MPAndroidChart visualizations

✅ **Trades**
- Complete trades list
- Real-time search/filter
- Trade performance indicators
- Clean Material Design 3 UI

✅ **Settings**
- Light/Dark/System theme selection
- CSV export functionality
- Data management
- App information

## Technical Stack

- **Language**: 100% Kotlin
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room (SQLite)
- **UI**: Material Design 3
- **Charts**: MPAndroidChart
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)

## Project Structure

```
TradeFlow-Android/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/tradeflow/
│       │   ├── MainActivity.kt
│       │   ├── data/
│       │   │   ├── Trade.kt
│       │   │   ├── TradeDao.kt
│       │   │   ├── AppDatabase.kt
│       │   │   └── SampleData.kt
│       │   ├── ui/
│       │   │   ├── dashboard/
│       │   │   ├── analytics/
│       │   │   ├── trades/
│       │   │   └── settings/
│       │   └── utils/
│       │       ├── ThemeManager.kt
│       │       ├── CsvExporter.kt
│       │       └── Extensions.kt
│       └── res/
│           ├── layout/
│           ├── values/
│           ├── values-night/
│           └── menu/
├── build.gradle
└── settings.gradle
```

## Building the App

### Prerequisites
- Android Studio Arctic Fox or later
- JDK 17
- Android SDK 34

### Build Instructions

1. **Open in Android Studio**:
   ```bash
   cd "TradeFlow-Android"
   # Then: File → Open → Select TradeFlow-Android folder
   ```

2. **Sync Gradle**:
   - Android Studio will prompt to sync Gradle
   - Click "Sync Now"

3. **Build Debug APK**:
   ```bash
   # Via terminal
   ./gradlew assembleDebug
   
   # Or in Android Studio: Build → Build Bundle(s) / APK(s) → Build APK(s)
   ```

4. **APK Location**:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Alternative: Build Without Android Studio

```bash
cd "/home/teedot/Documents/projects/Trading journal./TradeFlow-Android"

# Make gradlew executable
chmod +x gradlew

# Download Gradle wrapper (if needed)
gradle wrapper

# Build
./gradlew assembleDebug
```

## Installation

### Via ADB:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Via File Transfer:
1. Copy `app-debug.apk` to your Android device
2. Enable "Install from Unknown Sources" in Settings
3. Tap the APK file to install

## Sample Data

The app comes pre-loaded with 50 realistic mock trades featuring:
- Multiple symbols (AAPL, GOOGL, MSFT, etc.)
- 60% win rate
- Varied profit/loss amounts
- Different trading strategies
- Dates spanning the last 50 days

## Features Showcase

### Dashboard
- **Total P&L**: Cumulative profit/loss across all trades
- **Win Rate**: Percentage of winning trades
- **Profit Factor**: Ratio of gross profits to gross losses
- **Avg Return**: Average percentage return per trade

### Analytics
Three professional charts visualizing:
1. **Cumulative P&L trend** over time
2. **Win/Loss ratio** in an easy-to-read pie chart
3. **Performance by symbol** highlighting top/bottom performers

### Settings
- **Theme Control**: Switch between Light, Dark, or System default
- **Export**: Generate CSV files for external analysis
- **Data Management**: Clear all trades with confirmation

## Development

### Running in Debug Mode:
```bash
./gradlew installDebug
adb shell am start -n com.tradeflow/.MainActivity
```

### View Logs:
```bash
adb logcat | grep TradeFlow
```

## Future Enhancements

- [ ] Add new trade form
- [ ] Trade detail view
- [ ] PDF export
- [ ] Cloud sync
- [ ] Advanced filtering
- [ ] Trade statistics by strategy
- [ ] Multi-currency support

---

**Version**: 1.0.0  
**Package**: com.tradeflow  
**License**: MIT
