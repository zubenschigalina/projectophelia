// Top-level build file. We declare plugin versions here with `apply false`
// so that subprojects (like :app) can opt in via `id("...")` without re-stating versions.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // KSP (Kotlin Symbol Processing) — modern replacement for kapt.
    // Used here to generate Room's DAO/Database implementations at compile time.
    // The version *must* match our Kotlin version (2.0.21) plus a KSP suffix (-1.0.27).
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
}
