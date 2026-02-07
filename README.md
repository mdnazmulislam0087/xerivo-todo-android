# Xerivo To-Do (Android)

A modern Jetpack Compose To-Do app focused on fast task capture, clean list filtering, and polished UI.

## Current Features

- Local persistence (tasks + categories) using `SharedPreferences` + JSON
- Quick task capture with:
  - title
  - notes
  - due preset (`No Date`, `Today`, `Tomorrow`, `+7d`)
  - priority (`High`, `Medium`, `Low`)
  - repeat (`No Repeat`, `Daily`, `Weekly`)
  - category selection
- Custom category management (add/delete)
- Task operations:
  - complete/uncomplete
  - edit
  - archive/restore
  - delete with undo snackbar
- Recurring task auto-create on completion
- Filter tabs:
  - `All`
  - `Active`
  - `Done`
  - `Overdue`
- Professional list-only mode when tab filters are selected
- Refined UI pass:
  - improved spacing/typography
  - card depth and borders
  - smoother list animations

## Tech Stack

- Kotlin
- Jetpack Compose + Material 3
- Gradle (KTS)

## Prerequisites

- Android Studio (recommended)
- JDK 17+ (Android Studio bundled JBR works)
- Android SDK configured in Android Studio

## Build (Windows PowerShell)

From `xerivo-todo-android/XerivoTodo`:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug
```

Generated APK:

`XerivoTodo/app/build/outputs/apk/debug/app-debug.apk`

## Quality Checks

```powershell
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat testDebugUnitTest --console=plain
```

## Open In Android Studio

Open folder:

`xerivo-todo-android/XerivoTodo`

Then run the `app` configuration on an emulator/device.

## Project Structure

- `XerivoTodo/app/src/main/java/com/xerivo/todo/MainActivity.kt`  
  Main screen logic, UI, filters, task CRUD, and persistence integration
- `XerivoTodo/app/src/main/java/com/xerivo/todo/ui/theme/`  
  Theme, color, typography tokens

## Known Limitations (Current Version)

- No cloud sync/collaboration yet
- No OS-level reminders/notifications yet
- Due date input uses presets (no custom date picker yet)

## Suggested Next Steps

1. Add notification reminders
2. Add custom date/time picker
3. Add export/import backup
