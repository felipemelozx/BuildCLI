package dev.buildcli.core.utils.installers;

import dev.buildcli.core.exceptions.DownloadFailedException;
import dev.buildcli.core.log.SystemOutLogger;
import dev.buildcli.core.utils.DirectoryCleanup;
import dev.buildcli.core.utils.EnvUtils;
import dev.buildcli.core.utils.OS;
import dev.buildcli.core.utils.compress.FileExtractor;
import dev.buildcli.core.utils.net.FileDownloader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;

public abstract class MavenInstaller {
  private MavenInstaller() {
  }

  private static final String MAVEN_VERSION = "3.9.9";
  private static final String MAVEN_NAME = "apache-maven-%s".formatted(MAVEN_VERSION);
  private static final String MAVEN_DOWNLOAD_URL = "https://dlcdn.apache.org/maven/maven-3/%s/binaries/%s-bin.".formatted(MAVEN_VERSION, MAVEN_NAME);


  public static void installMaven() throws DownloadFailedException {
    SystemOutLogger.log("Installing Maven operation started...");
    try {
      SystemOutLogger.log("Downloading Maven operation started...");
      var file = downloadMaven();
      SystemOutLogger.log("Downloading Maven operation finished...");

      SystemOutLogger.log("Maven downloaded to " + file.getAbsolutePath());
      var outputFile = installProgramFilesDirectory();
      SystemOutLogger.log("Maven install path set to " + outputFile.getAbsolutePath());

      SystemOutLogger.log("Extracting Maven operation started...");
      extractMaven(file.getAbsolutePath(), outputFile.getAbsolutePath());
      SystemOutLogger.log("Extracting Maven operation finished...");

      SystemOutLogger.log("Configuring Maven path operation started...");
      configurePath(Paths.get(outputFile.getAbsolutePath(), MAVEN_NAME).toFile().getAbsolutePath());
      SystemOutLogger.log("Configuring Maven path operation finished...");

      if (file.exists()) {
        SystemOutLogger.log("Cleaning up maven download path...");
        DirectoryCleanup.cleanup(file.getAbsolutePath());
        SystemOutLogger.log("Cleaning up maven download path finished...");
      }
    } catch (IOException e) {
      SystemOutLogger.log("Failed to install Maven: " + e.getMessage());
      throw new DownloadFailedException("Failed to install Maven" + e.getMessage());
    }
  }

  public static File installProgramFilesDirectory() {
    if (OS.isWindows()) {
      String programFiles = EnvUtils.getEnv("ProgramFiles");
      if (programFiles == null) {
        programFiles = "C:\\Program Files";
      }
      return new File(programFiles, "Maven");
    } else {
      return new File("/usr/local/maven");
    }
  }

  public static File downloadMaven() throws IOException {
    var isWindows = OS.isWindows();
    var url = MAVEN_DOWNLOAD_URL + (isWindows ? "zip" : "tar.gz");

    SystemOutLogger.log("Downloading Maven artifact from: " + url);

    SystemOutLogger.log("Connecting to " + url);
    File response = FileDownloader.download(url);
    SystemOutLogger.log("Connected to " + url);
    if (!response.exists()) {
      throw new IOException("Failed to download Maven artifact from: " + url);
    }
    return response;
  }

  public static void extractMaven(String filePath, String extractTo) throws IOException {
    FileExtractor.extractFile(filePath, extractTo);
  }

  public static void configurePath(String mavenBinPath) throws IOException {
    var isWindows = OS.isWindows();
    if (isWindows) {
      Runtime.getRuntime().exec(new String[] {"setx PATH \"%PATH%;" + mavenBinPath  + "\\\\bin\""});
    } else {
      File bashrc = new File(System.getProperty("user.home"), ".bashrc");
      try (FileWriter fw = new FileWriter(bashrc, true)) {
        fw.write("\nexport PATH=$PATH:" + mavenBinPath + "/bin\n");
      }
      SystemOutLogger.log("Please run: source ~/.bashrc");
      SystemOutLogger.log("Please run: sudo chmod +x " + mavenBinPath + "/bin/mvn\n");
    }
  }
}
