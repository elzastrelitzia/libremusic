import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.chaquo)
}

android {
    val appId = "com.elza.pulse"

    namespace = "app.pulse.android"
    compileSdk = 37

    val abis = listOf("arm64-v8a")
    val cmakeVersion = "4.1.2"
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = appId

        minSdk = 24
        targetSdk = 36

        versionCode = System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull() ?: 30
        versionName = project.version.toString()

        multiDexEnabled = true

        ndk {
            abiFilters += abis
        }

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("ci") {
            storeFile = System.getenv("ANDROID_NIGHTLY_KEYSTORE")?.let { file(it) }
            storePassword = System.getenv("ANDROID_NIGHTLY_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_NIGHTLY_KEYSTORE_ALIAS")
            keyPassword = System.getenv("ANDROID_NIGHTLY_KEYSTORE_PASSWORD")
        }

        create("release") {
            storeFile = System.getenv("ANDROID_RELEASE_KEYSTORE")?.let { file(it) }
            storePassword = System.getenv("ANDROID_RELEASE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_RELEASE_KEYSTORE_ALIAS")
            keyPassword = System.getenv("ANDROID_RELEASE_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        debug {
            // separate id so debug and release install side by side
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appName"] = "libremusic Debug"
        }

        release {
            versionNameSuffix = "-RELEASE"
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["appName"] = "libremusic"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }

    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources.excludes.add("META-INF/**/*")
        jniLibs.useLegacyPackaging = true
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    externalNativeBuild {
        cmake {
            version = cmakeVersion
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    chaquopy {
        defaultConfig {
            buildPython(
                System.getenv("CHAQUOPY_PYTHON")
                    ?: localProperties.getProperty("chaquopy.python")
                    ?: "python3.11"
            )
            version = "3.11"
            pip {
                options("--pre")
                install("yt-dlp[default]")
                install("yt-dlp-ejs")
                install("spotifyscraper")
                install("pip")
            }
        }
    }
}

afterEvaluate {
    val jniLibs = file("${layout.projectDirectory}/src/main/jniLibs").also { it.mkdirs() }
    android.buildTypes.forEach { type ->
        val typeCapitalized = type.name.let {
            it.first().uppercase() + it.substring(1)
        }

        tasks.named("assemble${typeCapitalized}").configure {
            doFirst {
                val cxxDir =
                    file("${layout.buildDirectory.get()}/intermediates/cxx/${if (typeCapitalized == "Debug") "Debug" else "RelWithDebInfo"}")

                cxxDir.walkTopDown().forEach cxx@{ f ->
                    if (f.name != "qjs") return@cxx

                    f.copyTo(
                        target = jniLibs
                            .resolve(f.parentFile.name)
                            .also { it.mkdirs() }
                            .resolve("libqjs.so"), // disguise because fuck you
                        overwrite = true
                    )
                }
            }
        }
    }
}

kotlin {
    // jvmToolchain(libs.versions.jvm.get().toInt())

    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_2_5)
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)

        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xconsistent-data-class-copy-visibility"
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

composeCompiler {
    if (project.findProperty("enableComposeCompilerReports") == "true") {
        val dest = layout.buildDirectory.dir("compose_metrics")
        metricsDestination = dest
        reportsDestination = dest
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugaring)

    implementation(projects.compose.persist)
    implementation(projects.compose.preferences)
    implementation(projects.compose.routing)
    implementation(projects.compose.reordering)

    implementation(fileTree(projectDir.resolve("vendor")))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.activity)
    implementation(libs.splashscreen)

    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.shimmer)
    implementation(libs.compose.lottie)
    implementation(libs.compose.material3)

    implementation(libs.coil.compose)
    implementation(libs.coil.ktor)

    implementation(libs.compose.cloudy)

    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)

    implementation(libs.palette)
    implementation(libs.monet)
    runtimeOnly(projects.core.materialCompat)

    implementation(libs.exoplayer)
    implementation(libs.exoplayer.workmanager)
    implementation(libs.media3.session)
    implementation(libs.media)

    implementation(libs.workmanager)
    implementation(libs.workmanager.ktx)

    implementation(libs.credentials)
    implementation(libs.credentials.play)

    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlin.immutable)
    implementation(libs.kotlin.datetime)

    implementation(libs.room)
    ksp(libs.room.compiler)

    implementation(libs.log4j)
    implementation(libs.slf4j)
    implementation(libs.logback)

    implementation(projects.providers.github)
    implementation(projects.providers.innertube)
    implementation(projects.providers.kugou)
    implementation(projects.providers.lrclib)
    implementation(projects.providers.piped)
    implementation(projects.providers.sponsorblock)
    implementation(projects.providers.translate)
    implementation(projects.core.data)
    implementation(projects.core.ui)

    detektPlugins(libs.detekt.compose)
    detektPlugins(libs.detekt.formatting)
}
