plugins {
  kotlin("jvm") version "2.3.20"
}

repositories {
  mavenCentral()
}

dependencies {
  implementation(gradleApi())
  testImplementation(kotlin("test"))
}

tasks.test {
  useJUnitPlatform()
}
