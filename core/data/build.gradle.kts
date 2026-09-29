plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "app.pulse.core.data"
        compileSdk = 36
        minSdk = 21

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.coroutines)
            implementation(libs.kotlinx.serialization.json)
            implementation(projects.providers.innertube)
            api(libs.kotlin.datetime)
        }
        androidMain.dependencies {
            implementation(libs.core.ktx)
            implementation(libs.exoplayer)
            implementation(libs.media3.session)
            implementation(libs.room)
        }
        jvmMain.dependencies {
            implementation(libs.sqlite.jdbc)
        }
        jvmTest.dependencies {
            // implementation(...) is required. A bare kotlin("test") builds the notation and
            // adds it to no configuration, so the test compile classpath silently has no
            // kotlin-test on it and every import fails with "Unresolved reference 'test'".
            implementation(kotlin("test"))
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xnon-local-break-continue",
            "-Xconsistent-data-class-copy-visibility"
        )
    }
}

dependencies {
    detektPlugins(libs.detekt.compose)
    detektPlugins(libs.detekt.formatting)
}
