#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");
const {
  acquireDirectoryLock,
  beginPublishState,
  clearPublishState,
  collectAbiApkPaths,
  copyFile,
  readProperties,
  reconcilePublishState,
  walkFiles,
  writePropertiesAtomically,
} = require("./build-apks-lib");

const rootDir = __dirname;
const versionFile = path.join(rootDir, "version.properties");
const outputRoot = path.join(rootDir, "packages");
const apkOutputRoot = path.join(rootDir, "app", "build", "outputs", "apk");
const buildLockPath = path.join(outputRoot, ".build-apks.lock");
const publishStateFile = path.join(outputRoot, ".build-apks-state.json");
const allowedAbis = ["arm64-v8a", "x86_64"];
const appLabel = "lissen";

function runGradle(taskName, extraEnv = {}) {
  const gradleExecutable = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
  const env = {
    ...process.env,
    ...releaseSigningEnvironment(),
    ...extraEnv,
  };

  const result = spawnSync(
    gradleExecutable,
    [taskName, "--console=plain"],
    {
      cwd: rootDir,
      stdio: "inherit",
      shell: process.platform === "win32",
      env,
    },
  );

  if (result.status !== 0) {
    throw new Error(`Gradle task failed: ${taskName}`);
  }
}

function requireReleaseSigning() {
  const requiredKeys = [
    "RELEASE_STORE_FILE",
    "RELEASE_STORE_PASSWORD",
    "RELEASE_KEY_ALIAS",
    "RELEASE_KEY_PASSWORD",
  ];
  const signingEnvironment = releaseSigningEnvironment();
  const missingKeys = requiredKeys.filter((key) => !signingEnvironment[`ORG_GRADLE_PROJECT_${key}`]);

  if (missingKeys.length > 0) {
    throw new Error(
      `Missing release signing values: ${missingKeys.join(", ")}. Provide them via signing.properties or ORG_GRADLE_PROJECT_* env vars.`,
    );
  }
}

function releaseSigningEnvironment() {
  const gradleProperties = path.join(rootDir, "signing.properties");
  const signingState = fs.existsSync(gradleProperties) ? readProperties(gradleProperties) : {};

  return {
    ORG_GRADLE_PROJECT_RELEASE_STORE_FILE:
      process.env.ORG_GRADLE_PROJECT_RELEASE_STORE_FILE || signingState.RELEASE_STORE_FILE,
    ORG_GRADLE_PROJECT_RELEASE_STORE_PASSWORD:
      process.env.ORG_GRADLE_PROJECT_RELEASE_STORE_PASSWORD || signingState.RELEASE_STORE_PASSWORD,
    ORG_GRADLE_PROJECT_RELEASE_KEY_ALIAS:
      process.env.ORG_GRADLE_PROJECT_RELEASE_KEY_ALIAS || signingState.RELEASE_KEY_ALIAS,
    ORG_GRADLE_PROJECT_RELEASE_KEY_PASSWORD:
      process.env.ORG_GRADLE_PROJECT_RELEASE_KEY_PASSWORD || signingState.RELEASE_KEY_PASSWORD,
  };
}

function main() {
  fs.mkdirSync(outputRoot, { recursive: true });
  const releaseLock = acquireDirectoryLock(buildLockPath);
  let nextBuildNumber = null;
  let versionOutputDir = null;
  let versionStagingDir = null;
  let publishStateWritten = false;

  try {
    reconcilePublishState(publishStateFile, versionFile);

    const versionState = readProperties(versionFile);
    const baseVersion = versionState.BASE_VERSION;
    const currentBuildNumber = Number(versionState.BUILD_NUMBER);

    if (!baseVersion) {
      throw new Error("Missing BASE_VERSION in version.properties");
    }

    if (!Number.isInteger(currentBuildNumber)) {
      throw new Error("BUILD_NUMBER must be an integer in version.properties");
    }

    nextBuildNumber = currentBuildNumber + 1;
    const resolvedVersionName = `${baseVersion}.${nextBuildNumber}`;
    let copiedArtifacts = [];

    requireReleaseSigning();
    console.log(`Building version ${resolvedVersionName}`);
    runGradle(":app:assembleRelease", {
      ORG_GRADLE_PROJECT_BASE_VERSION: baseVersion,
      ORG_GRADLE_PROJECT_BUILD_NUMBER: String(nextBuildNumber),
    });

    const builtApks = collectAbiApkPaths(apkOutputRoot, allowedAbis);

    versionOutputDir = path.join(outputRoot, resolvedVersionName);
    versionStagingDir = path.join(outputRoot, `.tmp-${resolvedVersionName}-${process.pid}`);

    if (fs.existsSync(versionOutputDir)) {
      throw new Error(`Refusing to overwrite existing artifacts at ${versionOutputDir}.`);
    }

    fs.rmSync(versionStagingDir, { recursive: true, force: true });
    fs.mkdirSync(versionStagingDir, { recursive: true });

    copiedArtifacts = [];
    for (const abi of allowedAbis) {
      const sourcePath = builtApks.get(abi);
      const targetName = `${appLabel}-${resolvedVersionName}-${abi}-release.apk`;
      const targetPath = path.join(versionStagingDir, targetName);
      copyFile(sourcePath, targetPath);
      copiedArtifacts.push({
        abi,
        file: targetName,
        source: path.relative(rootDir, sourcePath),
      });
    }

    const metadata = {
      versionName: resolvedVersionName,
      versionCode: nextBuildNumber,
      buildType: "release",
      generatedAt: new Date().toISOString(),
      artifacts: copiedArtifacts,
    };
    fs.writeFileSync(
      path.join(versionStagingDir, "build-info.json"),
      `${JSON.stringify(metadata, null, 2)}\n`,
      "utf8",
    );

    beginPublishState(publishStateFile, {
      baseVersion,
      versionName: resolvedVersionName,
      versionCode: nextBuildNumber,
      versionOutputDir,
      versionStagingDir,
      generatedAt: metadata.generatedAt,
      artifacts: copiedArtifacts,
    });
    publishStateWritten = true;

    fs.renameSync(versionStagingDir, versionOutputDir);
    writePropertiesAtomically(versionFile, {
      BASE_VERSION: baseVersion,
      BUILD_NUMBER: nextBuildNumber,
    });
    clearPublishState(publishStateFile);

    console.log(`Artifacts stored in ${path.relative(rootDir, versionOutputDir)}`);
    for (const artifact of copiedArtifacts) {
      console.log(`- ${artifact.abi}: ${artifact.file}`);
    }
  } catch (error) {
    const versionPublished =
      nextBuildNumber != null &&
      fs.existsSync(versionFile) &&
      Number(readProperties(versionFile).BUILD_NUMBER) === nextBuildNumber;
    const outputPublished = versionOutputDir ? fs.existsSync(versionOutputDir) : false;

    if (!versionPublished && !outputPublished && publishStateWritten) {
      clearPublishState(publishStateFile);
    }

    if (versionStagingDir && fs.existsSync(versionStagingDir)) {
      fs.rmSync(versionStagingDir, { recursive: true, force: true });
    }

    throw error;
  } finally {
    releaseLock();
  }
}

main();
