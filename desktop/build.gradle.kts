plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.compose") version "1.11.1"
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    implementation(projects.providers.innertube)
    implementation(projects.core.data)
    implementation(projects.providers.kugou)
    implementation(projects.providers.lrclib)
    implementation(projects.providers.piped)
    implementation(projects.providers.github)
    implementation(projects.providers.sponsorblock)
    implementation(projects.providers.translate)
    implementation("org.slf4j:slf4j-simple:2.0.17")

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqlite.jdbc)
}

// Cross-platform native binary download task (works on Linux/macOS/Windows)
tasks.register("downloadNativeBinaries") {
    description = "Download static yt-dlp + ffmpeg binaries for bundling"

    doLast {
        val osName = System.getProperty("os.name").lowercase()
        val isWindows = osName.contains("win")

        val runner = if (isWindows) listOf("cmd", "/c", "download-binaries.bat")
            else listOf("bash", "download-binaries.sh")
        val proc = ProcessBuilder(runner)
            .directory(project.projectDir)
            .inheritIO()
            .start()
        val exit = proc.waitFor()
        if (exit != 0) throw GradleException("Binary download failed (exit $exit)")
    }
}

tasks.register("ensureWindowsScript") {
    doLast {
        val nativeDir = file("../core/data/src/jvmMain/resources/native/windows")
        val batFile = file("download-binaries.bat")
        if (!batFile.exists()) {
            batFile.writeText("""@echo off
setlocal
set NATIVE_DIR=..\core\data\src\jvmMain\resources\native\windows
if not exist "%NATIVE_DIR%" mkdir "%NATIVE_DIR%"

echo ==^> Downloading yt-dlp.exe for Windows...
curl -fsSL https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe -o "%NATIVE_DIR%\yt-dlp.exe"
echo     OK

echo ==^> Downloading ffmpeg.exe for Windows...
echo This may take a moment (large download)...
set FFMPEG_ZIP=%TEMP%\ffmpeg-static.zip
curl -fsSL https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip -o "%FFMPEG_ZIP%"
tar -xf "%FFMPEG_ZIP%" -C "%TEMP%" >nul 2>&1
copy /Y "%TEMP%\ffmpeg-master-latest-win64-gpl\bin\ffmpeg.exe" "%NATIVE_DIR%\ffmpeg.exe" >nul
rmdir /S /Q "%TEMP%\ffmpeg-master-latest-win64-gpl" 2>nul
del "%FFMPEG_ZIP%" 2>nul
echo     OK

echo.
echo ==^> Done. Binaries in %NATIVE_DIR%/
dir /B "%NATIVE_DIR%"
""")
            logger.warn("Created download-binaries.bat for Windows native binary download.")
        }
    }
}

tasks.named("downloadNativeBinaries") {
    dependsOn("ensureWindowsScript")
}

compose.desktop {
    application {
        mainClass = "app.pulse.desktop.MainKt"

        // ProGuard is left off on purpose. Shrinking removed three ServiceLoader providers
        // one at a time and each failed silently rather than loudly: slf4j-simple vanished
        // so the app ran on a NOP logger with no log output at all, sqlite-jdbc lost
        // org.sqlite.Function and died on startup, and ktor lost its JSON extension
        // provider. Every fix was another -keep rule and the next one is only findable by
        // running the app. Shrinking bought 18 MB, which is not worth a failure class that
        // deletes a library's entry point without saying so.
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        // The release runtime is jlinked, and Compose's default module list is only
        // java.base, java.desktop, java.logging and jdk.crypto.ec. sqlite-jdbc needs
        // java.sql, so the app died on startup with NoClassDefFoundError: java/sql/Driver.
        // List produced by ./gradlew :desktop:suggestRuntimeModules, minus the defaults.
        nativeDistributions {
            // Unset, so jpackage gets --name from project.name, which is "desktop". The
            // Windows launcher was desktop.exe and the AppImage AppRun had to exec
            // usr/lib/libremusic/bin/desktop. One name for every platform now.
            packageName = "libremusic"
            modules("java.instrument", "java.management", "java.sql", "jdk.unsupported")
        }
    }
}
