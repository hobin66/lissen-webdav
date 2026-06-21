const fs = require("fs");
const path = require("path");

function readProperties(filePath) {
  const content = fs.readFileSync(filePath, "utf8");
  return content
    .split(/\r?\n/)
    .filter((line) => line.trim() && !line.trim().startsWith("#"))
    .reduce((accumulator, line) => {
      const separatorIndex = line.indexOf("=");
      if (separatorIndex === -1) {
        return accumulator;
      }
      const key = line.slice(0, separatorIndex).trim();
      const value = line.slice(separatorIndex + 1).trim();
      accumulator[key] = value;
      return accumulator;
    }, {});
}

function serializeProperties(values) {
  return [
    `BASE_VERSION=${values.BASE_VERSION}`,
    `BUILD_NUMBER=${values.BUILD_NUMBER}`,
    "",
  ].join("\n");
}

function writeTextAtomically(filePath, content) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  const tempPath = path.join(
    path.dirname(filePath),
    `.tmp-${path.basename(filePath)}-${process.pid}-${Date.now()}`,
  );
  fs.writeFileSync(tempPath, content, "utf8");
  fs.renameSync(tempPath, filePath);
}

function writePropertiesAtomically(filePath, values) {
  writeTextAtomically(filePath, serializeProperties(values));
}

function writeJsonAtomically(filePath, value) {
  writeTextAtomically(filePath, `${JSON.stringify(value, null, 2)}\n`);
}

function walkFiles(directory) {
  if (!fs.existsSync(directory)) {
    return [];
  }

  const entries = fs.readdirSync(directory, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...walkFiles(fullPath));
      continue;
    }
    files.push(fullPath);
  }

  return files;
}

function copyFile(sourcePath, targetPath) {
  fs.mkdirSync(path.dirname(targetPath), { recursive: true });
  fs.copyFileSync(sourcePath, targetPath);
}

function acquireDirectoryLock(lockPath) {
  try {
    fs.mkdirSync(lockPath, { recursive: false });
  } catch (error) {
    if (error && error.code === "EEXIST") {
      throw new Error(`Another packaging run is already in progress: ${lockPath}`);
    }
    throw error;
  }

  let released = false;
  return () => {
    if (released) {
      return;
    }
    released = true;
    fs.rmSync(lockPath, { recursive: true, force: true });
  };
}

function collectAbiApkPaths(apkOutputRoot, allowedAbis) {
  const collected = new Map();

  for (const filePath of walkFiles(apkOutputRoot)) {
    if (!filePath.endsWith(".apk")) {
      continue;
    }

    const normalizedPath = filePath.replace(/\\/g, "/");
    if (!normalizedPath.includes("/release/")) {
      continue;
    }

    for (const abi of allowedAbis) {
      if (!normalizedPath.includes(abi)) {
        continue;
      }

      if (collected.has(abi)) {
        throw new Error(`Found multiple release APKs for ABI ${abi}.`);
      }

      collected.set(abi, filePath);
    }
  }

  const missingAbis = allowedAbis.filter((abi) => !collected.has(abi));
  if (missingAbis.length > 0) {
    throw new Error(`Missing release APKs for ABI: ${missingAbis.join(", ")}.`);
  }

  return collected;
}

function collectUniversalApkPath(apkOutputRoot) {
  let universalApkPath = null;

  for (const filePath of walkFiles(apkOutputRoot)) {
    if (!filePath.endsWith(".apk")) {
      continue;
    }

    const normalizedPath = filePath.replace(/\\/g, "/");
    if (!normalizedPath.includes("/release/")) {
      continue;
    }

    if (!normalizedPath.endsWith("/app-universal-release.apk")) {
      continue;
    }

    if (universalApkPath) {
      throw new Error("Found multiple universal release APKs.");
    }

    universalApkPath = filePath;
  }

  if (!universalApkPath) {
    throw new Error("Missing universal release APK.");
  }

  return universalApkPath;
}

function readPublishState(stateFilePath) {
  if (!fs.existsSync(stateFilePath)) {
    return null;
  }

  return JSON.parse(fs.readFileSync(stateFilePath, "utf8"));
}

function clearPublishState(stateFilePath) {
  fs.rmSync(stateFilePath, { force: true });
}

function versionsMatch(versionState, publishState) {
  return (
    versionState.BASE_VERSION === publishState.baseVersion &&
    Number(versionState.BUILD_NUMBER) === Number(publishState.versionCode)
  );
}

function reconcilePublishState(stateFilePath, versionFilePath) {
  const state = readPublishState(stateFilePath);
  if (!state) {
    return;
  }

  const versionState = readProperties(versionFilePath);
  const outputExists = fs.existsSync(state.versionOutputDir);

  if (outputExists && versionsMatch(versionState, state)) {
    clearPublishState(stateFilePath);
    return;
  }

  throw new Error(
    `Found incomplete packaging state at ${stateFilePath}. ` +
      `Resolve the partial publish for ${state.versionName} before retrying.`,
  );
}

function beginPublishState(stateFilePath, publishState) {
  if (fs.existsSync(publishState.versionOutputDir)) {
    throw new Error(`Refusing to overwrite existing artifacts at ${publishState.versionOutputDir}.`);
  }

  writeJsonAtomically(stateFilePath, publishState);
}

module.exports = {
  acquireDirectoryLock,
  beginPublishState,
  clearPublishState,
  collectAbiApkPaths,
  collectUniversalApkPath,
  copyFile,
  readProperties,
  readPublishState,
  reconcilePublishState,
  serializeProperties,
  walkFiles,
  writeJsonAtomically,
  writePropertiesAtomically,
};
