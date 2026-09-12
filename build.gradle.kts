// No explicit version on com.android.library: this module is included as a
// subproject by 12 consumer apps (each with its own root-level AGP version
// via their own version catalog), and Gradle rejects a subproject
// re-declaring an explicit version for a plugin the including root already
// manages ("already on the classpath with an unknown version"). Standalone
// builds of this module get their version from this repo's own
// settings.gradle.kts pluginManagement block instead.
// org.jetbrains.kotlin.android is intentionally not applied: AGP 9+ builds
// Kotlin support in directly, and separately applying that plugin is now a
// hard error ("no longer required ... since AGP 9.0").
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

group = "com.sayists.shyware"
version = "0.4.0"

android {
    namespace = "com.sayists.shyware"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        // targetSdk removed: not a valid defaultConfig property for a
        // library module's DSL under AGP 9+ (compile error, not just a
        // deprecation) -- it was never meaningful for a library anyway,
        // only for the manifest-merging application module.
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // kotlinOptions {} removed: AGP 9's built-in Kotlin support no longer
    // exposes this DSL (see the org.jetbrains.kotlin.android removal
    // above) -- JVM target is derived from compileOptions above.
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.android.play:integrity:1.3.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
