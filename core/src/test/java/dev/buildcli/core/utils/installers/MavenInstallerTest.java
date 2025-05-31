package dev.buildcli.core.utils.installers;

import dev.buildcli.core.exceptions.DownloadFailedException;
import dev.buildcli.core.log.SystemOutLogger;
import dev.buildcli.core.utils.DirectoryCleanup;
import dev.buildcli.core.utils.EnvUtils;
import dev.buildcli.core.utils.OS;
import dev.buildcli.core.utils.compress.FileExtractor;
import dev.buildcli.core.utils.net.FileDownloader;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MavenInstallerTest {

  private Path tempDir;
  private Path tempZipFile;

  private static final String MAVEN_VERSION = "3.9.9";
  private static final String MAVEN_NAME = "apache-maven-%s".formatted(MAVEN_VERSION);
  private static final String MAVEN_DOWNLOAD_URL = "https://dlcdn.apache.org/maven/maven-3/%s/binaries/%s-bin.".formatted(MAVEN_VERSION, MAVEN_NAME);

  @BeforeEach
  void setUp() throws IOException {
    tempDir = Files.createTempDirectory("maven-installer-test");
    tempZipFile = tempDir.resolve("maven-installer.zip");
    createEmptyZipFile(tempZipFile);
  }

  @AfterEach
  void tearDown() throws IOException {
    if (tempDir != null) {
      Files.walk(tempDir)
          .sorted(Comparator.reverseOrder())
          .map(Path::toFile)
          .forEach(File::delete);
    }
  }

  @Test
  void installMaven_shouldThrowDownloadFailedException_whenDownloadFails() {
    try (MockedStatic<MavenInstaller> mockedInstaller = mockStatic(MavenInstaller.class)) {
      // Setup: mock downloadMaven() para lançar IOException
      mockedInstaller.when(MavenInstaller::downloadMaven)
          .thenThrow(new IOException("Erro de download"));

      // Precisa permitir as outras chamadas estáticas que não causam erro
      mockedInstaller.when(MavenInstaller::installProgramFilesDirectory)
          .thenThrow(new IOException("Erro de download"));
      mockedInstaller.when(() -> MavenInstaller.extractMaven(anyString(), anyString()))
          .thenThrow(new IOException("Erro de download"));
      mockedInstaller.when(() -> MavenInstaller.configurePath(anyString()))
          .thenThrow(new IOException("Erro de download"));

      // Valida se a exceção esperada é lançada
      assertThrows(DownloadFailedException.class, MavenInstaller::installMaven);
    }
  }

  @Test
  void installMaven_shouldWorkOnLinux() throws Exception {
    try (MockedStatic<OS> osMock = mockStatic(OS.class);
         MockedStatic<FileDownloader> downloaderMock = mockStatic(FileDownloader.class);
         MockedStatic<FileExtractor> extractorMock = mockStatic(FileExtractor.class);
         MockedStatic<DirectoryCleanup> cleanupMock = mockStatic(DirectoryCleanup.class);
         MockedStatic<SystemOutLogger> loggerMock = mockStatic(SystemOutLogger.class)) {

      osMock.when(OS::isWindows).thenReturn(false);

      Path tempHome = Files.createTempDirectory("test-home");
      System.setProperty("user.home", tempHome.toString());

      File mockDownload = mock(File.class);
      when(mockDownload.exists()).thenReturn(true);
      when(mockDownload.getAbsolutePath()).thenReturn("/tmp/maven.tar.gz");

      String expectedUrl = MAVEN_DOWNLOAD_URL + "tar.gz";
      downloaderMock.when(() -> FileDownloader.download(expectedUrl))
          .thenReturn(mockDownload);

      MavenInstaller.installMaven();

      Path bashrcPath = tempHome.resolve(".bashrc");
      assertTrue(Files.exists(bashrcPath));
      String content = Files.readString(bashrcPath);
      String expectedPath = "/usr/local/maven/" + MAVEN_NAME + "/bin";
      assertTrue(content.contains("export PATH=$PATH:" + expectedPath));
    }
  }

  @Test
  void downloadMaven_shouldReturnFile_whenWindows() throws IOException {
    try (
        MockedStatic<OS> osMock = mockStatic(OS.class);
        MockedStatic<FileDownloader> downloaderMock = mockStatic(FileDownloader.class)
    ) {
      osMock.when(OS::isWindows).thenReturn(true);
      File mockFile = mock(File.class);
      when(mockFile.exists()).thenReturn(true);
      when(mockFile.getName()).thenReturn("apache-maven-3.9.9-bin.zip");
      downloaderMock.when(() -> FileDownloader.download(
          "https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip"
      )).thenReturn(mockFile);

      File result = MavenInstaller.downloadMaven();

      assertNotNull(result);
      assertEquals(mockFile.getName(), result.getName());
      downloaderMock.verify(() -> FileDownloader.download(
          "https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip"
      ), times(1));
    }
  }

  @Test
  void downloadMaven_shouldReturnFile_whenLinuxOrMac() throws IOException {
    try (
        MockedStatic<OS> osMock = mockStatic(OS.class);
        MockedStatic<FileDownloader> downloaderMock = mockStatic(FileDownloader.class)
    ) {
      osMock.when(OS::isWindows).thenReturn(false);
      File mockFile = mock(File.class);
      when(mockFile.exists()).thenReturn(true);
      when(mockFile.getName()).thenReturn("apache-maven-3.9.9-bin.tar.gz");
      downloaderMock.when(() -> FileDownloader.download(
          "https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz"
      )).thenReturn(mockFile);

      File result = MavenInstaller.downloadMaven();

      assertNotNull(result);
      assertEquals(mockFile.getName(), result.getName());
      downloaderMock.verify(() -> FileDownloader.download(
          "https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz"
      ), times(1));
    }
  }

  @Test
  void downloadMaven_shouldThrowsIoException_whenNotFile() throws IOException {
    try (
        MockedStatic<OS> osMock = mockStatic(OS.class);
        MockedStatic<FileDownloader> downloaderMock = mockStatic(FileDownloader.class)
    ) {
      osMock.when(OS::isWindows).thenReturn(false);
      File mockFile = mock(File.class);
      when(mockFile.exists()).thenReturn(false);
      when(mockFile.getName()).thenReturn("apache-maven-3.9.9-bin.tar.gz");
      downloaderMock.when(() -> FileDownloader.download(
          "https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz"
      )).thenReturn(mockFile);

      assertThrows(IOException.class, () -> {
        MavenInstaller.downloadMaven();
      });
    }
  }

  @Test
  void installProgramFilesDirectory_shouldReturnWindowsPath_whenEnvIsSet() {
    try (
        MockedStatic<OS> osMock = mockStatic(OS.class);
        MockedStatic<EnvUtils> envUtilsMock = mockStatic(EnvUtils.class)
    ) {
      osMock.when(OS::isWindows).thenReturn(true);
      envUtilsMock.when(() -> EnvUtils.getEnv("ProgramFiles")).thenReturn("D:\\Program Files");

      File result = MavenInstaller.installProgramFilesDirectory();

      assertTrue(result.toString().contains("D:\\Program Files"));
    }
  }

  @Test
  void installProgramFilesDirectory_shouldFallbackToDefault_whenEnvIsNull() {
    try (
        MockedStatic<OS> osMock = mockStatic(OS.class);
        MockedStatic<EnvUtils> envUtilsMock = mockStatic(EnvUtils.class);
    ) {
      osMock.when(OS::isWindows).thenReturn(true);
      envUtilsMock.when(() -> EnvUtils.getEnv("ProgramFiles")).thenReturn(null);

      File result = MavenInstaller.installProgramFilesDirectory();
      assertTrue(result.toPath().toString().contains("C:\\Program Files/Maven"));

    }
  }

  @Test
  void installMaven_shouldInstallMaven_InLinux() {
    try(MockedStatic<OS> osMock = mockStatic(OS.class)){
        osMock.when(OS::isWindows).thenReturn(false);
        File result = MavenInstaller.installProgramFilesDirectory();
        assertTrue(result.toPath().toString().contains("/usr/local/maven"));
    }
  }

  @Test
  void configurePath_shouldWriteToBashrcAndLog_onWindows() throws Exception {
    try (MockedStatic<OS> osMock = mockStatic(OS.class);
         MockedStatic<SystemOutLogger> loggerMock = mockStatic(SystemOutLogger.class);
         MockedStatic<Runtime> runtimeMocked = mockStatic(Runtime.class)) {

      osMock.when(OS::isWindows).thenReturn(true);
      Runtime mockRuntime = mock(Runtime.class);

      runtimeMocked.when(Runtime::getRuntime).thenReturn(mockRuntime);

      String fakePath = "C:\\fake\\maven";
      MavenInstaller.configurePath(fakePath);
      String expectedCommand = "setx PATH \"%PATH%;" + fakePath + "\\\\bin\"";
      verify(mockRuntime).exec(new String[]{expectedCommand});
      loggerMock.verifyNoInteractions();
    }
  }

  @Test
  void configurePath_shouldWriteToBashrcAndLog_onLinux() throws Exception {
    try (MockedStatic<OS> osMock = mockStatic(OS.class);
         MockedStatic<SystemOutLogger> loggerMock = mockStatic(SystemOutLogger.class)) {

      osMock.when(OS::isWindows).thenReturn(false);

      File tempHome = Files.createTempDirectory("fakeHome").toFile();
      File bashrc = new File(tempHome, ".bashrc");
      System.setProperty("user.home", tempHome.getAbsolutePath());

      String path = "/fake/maven/path";
      MavenInstaller.configurePath(path);

      String content = Files.readString(bashrc.toPath());
      assertTrue(content.contains("export PATH=$PATH:" + path + "/bin"));

      loggerMock.verify(() -> SystemOutLogger.log("Please run: source ~/.bashrc"));
      loggerMock.verify(() -> SystemOutLogger.log("Please run: sudo chmod +x " + path + "/bin/mvn\n"));
    }
  }

  @Test
  void shouldVoid_whenInstallCalled() throws IOException {
    MavenInstaller.extractMaven(tempZipFile.toString(), tempDir.toString());

    Path extractedFile = tempDir.resolve("dummy.txt");
    assertTrue(Files.exists(extractedFile), "Arquivo dummy.txt deveria existir após a extração");

    String content = Files.readString(extractedFile);
    assertEquals("dummy", content);
  }

  private void createEmptyZipFile(Path zipFilePath) throws IOException {
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
      ZipEntry entry = new ZipEntry("dummy.txt");
      zos.putNextEntry(entry);
      zos.write("dummy".getBytes());
      zos.closeEntry();
    }
  }
}
