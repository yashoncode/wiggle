buildscript {
    dependencies {
        // AGP 9 carries its own Kotlin. Putting the Kotlin Gradle plugin on the buildscript
        // classpath is what makes the `kotlin { compilerOptions { } }` DSL resolvable in :app.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.room) apply false
}
