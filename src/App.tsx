/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export default function App() {
  const projectFiles = [
    '.github/workflows/build-android.yml',
    'gradlew',
    'gradlew.bat',
    'gradle/wrapper/gradle-wrapper.jar',
    'gradle/wrapper/gradle-wrapper.properties',
    'gradle/libs.versions.toml',
    'settings.gradle.kts',
    'build.gradle.kts',
    'gradle.properties',
    'app/build.gradle.kts',
    'app/src/main/AndroidManifest.xml',
    'app/src/main/java/com/airmouse/hidtest/MainActivity.kt',
    'app/src/main/java/com/airmouse/hidtest/hid/HidMouseDescriptor.kt',
    'app/src/main/java/com/airmouse/hidtest/hid/HidState.kt',
    'app/src/main/java/com/airmouse/hidtest/hid/BluetoothHidManager.kt',
    'app/src/main/java/com/airmouse/hidtest/permissions/BluetoothPermissionHelper.kt',
    'app/src/main/java/com/airmouse/hidtest/viewmodel/AirMouseViewModel.kt',
    'app/src/main/java/com/airmouse/hidtest/ui/AirMouseApp.kt',
    'app/src/main/java/com/airmouse/hidtest/ui/screens/TouchpadScreen.kt',
    'app/src/main/java/com/airmouse/hidtest/ui/screens/ConnectionScreen.kt',
    'app/src/main/java/com/airmouse/hidtest/ui/screens/DiagnosticsScreen.kt',
    'app/src/test/java/com/airmouse/hidtest/HidMouseDescriptorTest.kt',
    'README.md',
  ];

  return (
    <main className="min-h-screen bg-[#0B0E14] text-[#F1F5F9] p-6 md:p-10 font-sans">
      <div className="max-w-3xl mx-auto space-y-6">
        <header className="border-b border-[#232D40] pb-4">
          <p className="text-xs font-mono uppercase tracking-widest text-[#38BDF8]">
            Native Android Kotlin Project • minSdk 34 • targetSdk 36 • GitHub Actions CI
          </p>
          <h1 className="text-2xl font-semibold mt-1">AirMouse HID Test</h1>
          <p className="text-sm text-[#94A3B8] mt-1">
            This repository contains a genuine native Android application written in Kotlin using{' '}
            <code className="text-[#38BDF8]">android.bluetooth.BluetoothHidDevice</code>, plus an
            automated GitHub Actions workflow that builds and delivers the Debug APK. No web/React
            mouse app or simulated Bluetooth stack is rendered here.
          </p>
        </header>

        <section className="rounded-xl bg-[#121722] border border-[#232D40] p-5 space-y-3">
          <h2 className="text-sm font-semibold uppercase tracking-wider text-[#94A3B8]">
            Automatic Cloud APK Build (No PC Needed)
          </h2>
          <ol className="list-decimal list-inside text-sm text-[#CBD5E1] space-y-1.5">
            <li>Connect or upload this project to a GitHub repository on the <code className="font-mono text-[#38BDF8]">main</code> branch.</li>
            <li>GitHub Actions (<code className="font-mono text-[#38BDF8]">.github/workflows/build-android.yml</code>) automatically runs unit tests and compiles the Debug APK.</li>
            <li>Open the GitHub <strong>Actions</strong> tab, tap the latest green run, download <code className="font-mono text-[#38BDF8]">AirMouse-Debug-APK</code>, extract <code className="font-mono text-[#38BDF8]">app-debug.apk</code>, and install on Android 14+.</li>
          </ol>
        </section>

        <section className="rounded-xl bg-[#121722] border border-[#232D40] p-5 space-y-3">
          <h2 className="text-sm font-semibold uppercase tracking-wider text-[#94A3B8]">
            Generated Native Android Files ({projectFiles.length})
          </h2>
          <ul className="grid grid-cols-1 gap-1.5 font-mono text-xs text-[#CBD5E1]">
            {projectFiles.map((file) => (
              <li
                key={file}
                className="px-3 py-1.5 rounded bg-[#0E131D] border border-[#1E2738]"
              >
                {file}
              </li>
            ))}
          </ul>
        </section>
      </div>
    </main>
  );
}
