plugins {
	alias(libs.plugins.android.library)
	
	id("com.google.devtools.ksp")
}

android {
	namespace = "io.github.hobin66.webdavplayer.lib"
	compileSdk = 36
	
	defaultConfig {
		minSdk = 28
		
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		consumerProguardFiles("consumer-rules.pro")
	}
	
	buildTypes {
		release {
			isMinifyEnabled = false
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
	}
}

dependencies {
	implementation(libs.androidx.core.ktx)
	implementation(libs.material)
	
	implementation(libs.moshi)
	
	ksp(libs.moshi.kotlin.codegen)
}
