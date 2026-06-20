const fs = require("fs");
const os = require("os");
const path = require("path");
const test = require("node:test");
const assert = require("node:assert/strict");

const {
  acquireDirectoryLock,
  beginPublishState,
  clearPublishState,
  collectAbiApkPaths,
  readProperties,
  reconcilePublishState,
  serializeProperties,
  writePropertiesAtomically,
} = require("./build-apks-lib");

function withTempDir(run) {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "build-apks-test-"));
  try {
    run(tempDir);
  } finally {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
}

test("serializeProperties keeps version file stable", () => {
  assert.equal(
    serializeProperties({
      BASE_VERSION: "1.9.6",
      BUILD_NUMBER: 10911,
    }),
    "BASE_VERSION=1.9.6\nBUILD_NUMBER=10911\n",
  );
});

test("writePropertiesAtomically replaces the full file contents", () => {
  withTempDir((tempDir) => {
    const filePath = path.join(tempDir, "version.properties");
    fs.writeFileSync(filePath, "BASE_VERSION=1.9.5\nBUILD_NUMBER=10910\n", "utf8");

    writePropertiesAtomically(filePath, {
      BASE_VERSION: "1.9.6",
      BUILD_NUMBER: 10911,
    });

    assert.deepEqual(readProperties(filePath), {
      BASE_VERSION: "1.9.6",
      BUILD_NUMBER: "10911",
    });
  });
});

test("acquireDirectoryLock rejects concurrent runs", () => {
  withTempDir((tempDir) => {
    const lockPath = path.join(tempDir, ".build-apks.lock");
    const release = acquireDirectoryLock(lockPath);

    assert.throws(
      () => acquireDirectoryLock(lockPath),
      /already in progress/,
    );

    release();
    const secondRelease = acquireDirectoryLock(lockPath);
    secondRelease();
  });
});

test("collectAbiApkPaths requires exactly one release apk per ABI", () => {
  withTempDir((tempDir) => {
    const outputRoot = path.join(tempDir, "app", "build", "outputs", "apk", "release");
    fs.mkdirSync(outputRoot, { recursive: true });
    fs.writeFileSync(path.join(outputRoot, "app-arm64-v8a-release.apk"), "");
    fs.writeFileSync(path.join(outputRoot, "app-x86_64-release.apk"), "");

    const result = collectAbiApkPaths(path.join(tempDir, "app", "build", "outputs", "apk"), [
      "arm64-v8a",
      "x86_64",
    ]);

    assert.equal(result.get("arm64-v8a"), path.join(outputRoot, "app-arm64-v8a-release.apk"));
    assert.equal(result.get("x86_64"), path.join(outputRoot, "app-x86_64-release.apk"));
  });
});

test("beginPublishState refuses to overwrite an existing version directory", () => {
  withTempDir((tempDir) => {
    const outputDir = path.join(tempDir, "packages", "1.9.6.10911");
    const stateFile = path.join(tempDir, "packages", ".build-apks-state.json");
    fs.mkdirSync(outputDir, { recursive: true });

    assert.throws(
      () =>
        beginPublishState(stateFile, {
          baseVersion: "1.9.6",
          versionCode: 10911,
          versionName: "1.9.6.10911",
          versionOutputDir: outputDir,
        }),
      /Refusing to overwrite existing artifacts/,
    );
  });
});

test("reconcilePublishState clears stale state after a completed publish", () => {
  withTempDir((tempDir) => {
    const packagesDir = path.join(tempDir, "packages");
    const outputDir = path.join(packagesDir, "1.9.6.10911");
    const stateFile = path.join(packagesDir, ".build-apks-state.json");
    const versionFile = path.join(tempDir, "version.properties");
    fs.mkdirSync(outputDir, { recursive: true });
    fs.mkdirSync(packagesDir, { recursive: true });
    writePropertiesAtomically(versionFile, {
      BASE_VERSION: "1.9.6",
      BUILD_NUMBER: 10911,
    });
    fs.writeFileSync(
      stateFile,
      JSON.stringify(
        {
          baseVersion: "1.9.6",
          versionCode: 10911,
          versionName: "1.9.6.10911",
          versionOutputDir: outputDir,
        },
        null,
        2,
      ),
      "utf8",
    );

    reconcilePublishState(stateFile, versionFile);

    assert.equal(fs.existsSync(stateFile), false);
  });
});

test("reconcilePublishState fails when a partial publish is still pending", () => {
  withTempDir((tempDir) => {
    const packagesDir = path.join(tempDir, "packages");
    const outputDir = path.join(packagesDir, "1.9.6.10911");
    const stateFile = path.join(packagesDir, ".build-apks-state.json");
    const versionFile = path.join(tempDir, "version.properties");
    fs.mkdirSync(packagesDir, { recursive: true });
    writePropertiesAtomically(versionFile, {
      BASE_VERSION: "1.9.6",
      BUILD_NUMBER: 10910,
    });
    fs.writeFileSync(
      stateFile,
      JSON.stringify(
        {
          baseVersion: "1.9.6",
          versionCode: 10911,
          versionName: "1.9.6.10911",
          versionOutputDir: outputDir,
        },
        null,
        2,
      ),
      "utf8",
    );

    assert.throws(
      () => reconcilePublishState(stateFile, versionFile),
      /incomplete packaging state/,
    );

    clearPublishState(stateFile);
  });
});
