package org.bitcoindevkit.plugins

import org.gradle.kotlin.dsl.register

val llvmArchPath = when (operatingSystem) {
    OS.MAC   -> "darwin-x86_64"
    OS.LINUX -> "linux-x86_64"
    OS.OTHER -> throw Error("Cannot build Android library from current architecture")
}

// arm64-v8a is the most popular hardware architecture for Android
val buildAndroidAarch64Binary by tasks.register<Exec>("buildAndroidAarch64Binary") {

    workingDir("${project.projectDir}/../bdk-ffi")
    val cargoArgs: MutableList<String> = mutableListOf("build", "--release", "--target", "aarch64-linux-android")

    executable("cargo")
    args(cargoArgs)

    // if ANDROID_NDK_ROOT is not set then set it to github actions default
    if (System.getenv("ANDROID_NDK_ROOT") == null) {
        environment(
            Pair("ANDROID_NDK_ROOT", "${System.getenv("ANDROID_SDK_ROOT")}/ndk-bundle")
        )
    }

    environment(
        // add build toolchain to PATH
        Pair("PATH", "${System.getenv("PATH")}:${System.getenv("ANDROID_NDK_ROOT")}/toolchains/llvm/prebuilt/$llvmArchPath/bin"),

        Pair("CFLAGS", "-D__ANDROID_API__=21"),
        Pair("CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER", "aarch64-linux-android21-clang"),
        Pair("CC", "aarch64-linux-android21-clang")
    )

    doLast {
        println("Native library for bdk-android on aarch64 built successfully")
    }
}

// the x86_64 version of the library is mostly used by emulators
val buildAndroidX86_64Binary by tasks.register<Exec>("buildAndroidX86_64Binary") {

    workingDir("${project.projectDir}/../bdk-ffi")
    val cargoArgs: MutableList<String> = mutableListOf("build", "--release", "--target", "x86_64-linux-android")

    executable("cargo")
    args(cargoArgs)

    // if ANDROID_NDK_ROOT is not set then set it to github actions default
    if (System.getenv("ANDROID_NDK_ROOT") == null) {
        environment(
            Pair("ANDROID_NDK_ROOT", "${System.getenv("ANDROID_SDK_ROOT")}/ndk-bundle")
        )
    }

    environment(
        // add build toolchain to PATH
        Pair("PATH", "${System.getenv("PATH")}:${System.getenv("ANDROID_NDK_ROOT")}/toolchains/llvm/prebuilt/$llvmArchPath/bin"),

        Pair("CFLAGS", "-D__ANDROID_API__=21"),
        Pair("CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER", "x86_64-linux-android21-clang"),
        Pair("CC", "x86_64-linux-android21-clang")
    )

    doLast {
        println("Native library for bdk-android on x86_64 built successfully")
    }
}

// move the native libs build by cargo from bdk-ffi/target/<architecture>/release/
// to their place in the bdk-android library
// the task only copies the available binaries built using the buildAndroid<architecture>Binary tasks
val moveNativeAndroidLibs by tasks.register<Copy>("moveNativeAndroidLibs") {

    dependsOn(buildAndroidAarch64Binary)

    into("${project.projectDir}/../android/src/main/jniLibs/")

    into("arm64-v8a") {
        from("${project.projectDir}/../bdk-ffi/target/aarch64-linux-android/release/libbdkffi.so")
    }

    into("x86_64") {
        from("${project.projectDir}/../bdk-ffi/target/x86_64-linux-android/release/libbdkffi.so")
    }

    doLast {
        println("Native binaries for Android moved to ./android/src/main/jniLibs/")
    }
}

// generate the bindings using the bdk-ffi-bindgen tool located in the bdk-ffi submodule
val generateAndroidBindings by tasks.register<Exec>("generateAndroidBindings") {
    dependsOn(moveNativeAndroidLibs)

    workingDir("${project.projectDir}/../bdk-ffi")
    executable("cargo")
    args("run", "--package", "bdk-ffi-bindgen", "--", "--language", "kotlin", "--out-dir", "../android/src/main/kotlin")

    doLast {
        println("Android bindings file successfully created")
    }
}

// create an aggregate task which will run the required tasks to build the Android libs in order
// the task will also appear in the printout of the ./gradlew tasks task with group and description
tasks.register("buildAndroidLib") {
    group = "Bitcoindevkit"
    description = "Aggregate task to build Android library"

    dependsOn(
        buildAndroidAarch64Binary,
        buildAndroidX86_64Binary,
        moveNativeAndroidLibs,
        generateAndroidBindings
    )
}
