// Top-level build file — only plugin declarations go here.
// Module-level build files (app/build.gradle.kts) contain actual dependencies.
plugins {
    alias(libs.plugins.android.application) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
