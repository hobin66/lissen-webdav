import java.util.Properties
import io.github.hobin66.webdavplayer.buildlogic.missingRequiredProperties
import io.github.hobin66.webdavplayer.buildlogic.requiresReleaseSigning
import io.github.hobin66.webdavplayer.buildlogic.resolveGitHash
import io.github.hobin66.webdavplayer.buildlogic.runGitShortHead

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  
  id("com.google.dagger.hilt.android")
  id("org.jmailen.kotlinter") version "5.4.2"
  id("com.google.devtools.ksp")
  id("kotlin-parcelize")
}

kotlinter {
  reporters = arrayOf("checkstyle", "plain")
  ignoreFormatFailures = false
  ignoreLintFailures = false
}

val versionPropertiesFile = rootProject.file("version.properties")

val versionProperties =
  Properties().apply {
    versionPropertiesFile.takeIf { it.exists() }?.inputStream()?.use { load(it) }
  }

val signingProperties =
  Properties().apply {
    rootProject.file("signing.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
  }

fun versionProperty(name: String): String =
  providers
    .gradleProperty(name)
    .orNull
    ?.takeIf { it.isNotBlank() }
    ?: versionProperties.getProperty(name)?.takeIf { it.isNotBlank() }
    ?: error("Missing $name. Provide it via Gradle property or version.properties")

fun signingProperty(name: String): String? =
  providers
    .gradleProperty(name)
    .orNull
    ?.takeIf { it.isNotBlank() }
    ?: signingProperties.getProperty(name)?.takeIf { it.isNotBlank() }

val baseVersionName = versionProperty("BASE_VERSION")
val buildNumber =
  versionProperty("BUILD_NUMBER").toIntOrNull()
    ?: error("BUILD_NUMBER must be an integer in version.properties")
val resolvedVersionName = "$baseVersionName.$buildNumber"
val hasReleaseSigning =
  listOf(
      "RELEASE_STORE_FILE",
      "RELEASE_STORE_PASSWORD",
      "RELEASE_KEY_ALIAS",
      "RELEASE_KEY_PASSWORD",
    )
    .all { propertyName -> !signingProperty(propertyName).isNullOrBlank() }
val missingReleaseSigningProperties =
  missingRequiredProperties(
    propertyNames =
      listOf(
        "RELEASE_STORE_FILE",
        "RELEASE_STORE_PASSWORD",
        "RELEASE_KEY_ALIAS",
        "RELEASE_KEY_PASSWORD",
      ),
    lookup = ::signingProperty,
  )

tasks.named("check") {
  dependsOn("lintKotlin")
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

fun gitCommitHash(): String {
  return resolveGitHash(runGitShortHead(rootProject.projectDir))
}

gradle.taskGraph.whenReady {
  if (requiresReleaseSigning(allTasks.map { it.name }) && !hasReleaseSigning) {
    error(
      buildString {
        append("Release signing is required for release artifact tasks. ")
        append("Missing properties: ")
        append(missingReleaseSigningProperties.joinToString())
        append(". Provide them via Gradle properties or signing.properties.")
      },
    )
  }
}

android {
  namespace = "io.github.hobin66.webdavplayer"
  compileSdk = 36

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = rootProject.file(signingProperty("RELEASE_STORE_FILE")!!)
        storePassword = signingProperty("RELEASE_STORE_PASSWORD")
        keyAlias = signingProperty("RELEASE_KEY_ALIAS")
        keyPassword = signingProperty("RELEASE_KEY_PASSWORD")
        enableV1Signing = true
        enableV2Signing = true
      }
    }
  }
  
  lint {
    disable.add("TypographyQuotes")
  }
  
  defaultConfig {
    val commitHash = gitCommitHash()
    
    applicationId = "io.github.hobin66.webdavplayer.app"
    minSdk = 28
    targetSdk = 36
    versionCode = buildNumber
    versionName = resolvedVersionName
    
    buildConfigField("String", "GIT_HASH", "\"$commitHash\"")
    buildConfigField("String", "APP_VERSION_NAME", "\"$resolvedVersionName\"")
    
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  
  buildTypes {
    release {
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
      )
    }
    debug {
      applicationIdSuffix = ".dev"
      versionNameSuffix = " (DEBUG)"
      matchingFallbacks.add("release")
      isDebuggable = true
    }
  }
  
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  
  buildFeatures {
    buildConfig = true
    compose = true
  }
  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a", "x86_64")
      isUniversalApk = false
    }
  }
  packaging {
    jniLibs {
      // WSA and some sideload installers are more reliable when native libraries
      // are stored with legacy packaging and extracted during install.
      useLegacyPackaging = true
      keepDebugSymbols +=
        setOf(
          "**/libandroidx.graphics.path.so",
          "**/libffmpegJNI.so",
          "**/libhoko_blur.so",
        )
    }
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1,MIT}"
    }
  }
  testOptions {
    packaging {
      resources {
        excludes += "META-INF/LICENSE.md"
        excludes += "META-INF/LICENSE-notice.md"
      }
    }
  }
  buildToolsVersion = "36.0.0"

  testOptions {
    unitTests.all {
      it.useJUnitPlatform()
    }
  }
}

dependencies {
  implementation(project(":lib"))
  
  implementation(libs.androidx.navigation.compose)
  implementation(libs.material)
  implementation(libs.material3)
  
  implementation(libs.androidx.media3.ffmpeg.decoder)
  implementation(libs.process.phoenix)
  implementation(libs.androidx.material)
  implementation(libs.compose.shimmer.android)
  
  implementation(libs.logging.interceptor)
  implementation(libs.okhttp)
  
  implementation(libs.coil.compose)
  implementation(libs.coil.svg)
  implementation(libs.hoko.blur)
  
  implementation(libs.androidx.paging.compose)
  
  implementation(libs.androidx.compose.material.icons.extended)
  
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.hilt.android)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.datasource.okhttp)
  implementation(libs.androidx.lifecycle.service)
  implementation(libs.androidx.lifecycle.process)
  
  ksp(libs.androidx.room.compiler)
  ksp(libs.hilt.android.compiler)
  ksp(libs.moshi.kotlin.codegen)
  
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.runtime.livedata)
  
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.dash)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.datasource)
  implementation(libs.androidx.media3.database)
  
  implementation(libs.androidx.localbroadcastmanager)
  implementation(libs.timber)
  
  implementation(libs.androidx.glance)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.glance.material3)

  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  
  implementation(libs.moshi)
  
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)

  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)

  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.mockk.android)
}
