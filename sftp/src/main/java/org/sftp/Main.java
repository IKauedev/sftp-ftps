package org.sftp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Main {

    public static void main(String[] args) {

        String host = "localhost";
        int port = 2222;
        String username = "testuser";
        String password = "testpass";

        org.sftp.RemoteTransferClient sftp = new SFTP(host, port, username, password);

        // Cria diretórios locais e arquivos para upload
        createLocalDirectoriesAndFiles();

        // Teste upload - envia os arquivos locais para o servidor
        System.out.println("=== Teste Upload: Enviar arquivos locais para SFTP ===");
        boolean uploadOk = sftp.moveToRemote(ConfigPaths.LOCAL_BASE_DIR, "/upload", true);
        System.out.println("Resultado upload pasta base: " + uploadOk);

        boolean uploadLogsOk = sftp.moveToRemote(ConfigPaths.LOCAL_LOGS_DIR, "/upload/logs", true);
        System.out.println("Resultado upload pasta logs: " + uploadLogsOk);

        // Testes download
        System.out.println("\n=== Teste 1: Download de arquivo único ===");
        boolean ok1 = sftp.moveToLocal(ConfigPaths.REMOTE_SINGLE_FILE, ConfigPaths.LOCAL_BASE_DIR, true);
        System.out.println("Resultado: " + ok1);

        System.out.println("\n=== Teste 2: Download de arquivos com wildcard *.csv ===");
        boolean ok2 = sftp.moveToLocal(ConfigPaths.REMOTE_CSV_FILES, ConfigPaths.LOCAL_BASE_DIR, true);
        System.out.println("Resultado: " + ok2);

        System.out.println("\n=== Teste 3: Download recursivo de tudo na pasta /upload/logs ===");
        boolean ok3 = sftp.moveToLocal(ConfigPaths.REMOTE_LOGS_DIR, ConfigPaths.LOCAL_LOGS_DIR, true);
        System.out.println("Resultado: " + ok3);

        createRecursiveLocalStructure();
        System.out.println("\n=== Teste Upload Recursivo ===");
        boolean uploadRecursiveOk = sftp.moveToRemote(ConfigPaths.LOCAL_BASE_DIR, "/upload/recursive_test", true);
        System.out.println("Upload recursivo OK: " + uploadRecursiveOk);

        String localDownloadRecursive = ConfigPaths.LOCAL_BASE_DIR + "/download_recursive";

        System.out.println("\n=== Teste Download Recursivo ===");
        boolean downloadRecursiveOk = sftp.moveToLocal("/upload/recursive_test", localDownloadRecursive, true);
        System.out.println("Download recursivo OK: " + downloadRecursiveOk);

        System.out.println("\nConteúdo baixado:");
        printDirectoryTree(new File(localDownloadRecursive), "");

        System.out.println("\n=== Teste 4: Ignorar operação (isMain = false) ===");
        boolean ok4 = sftp.moveToLocal(ConfigPaths.REMOTE_IGNORE_TEST, ConfigPaths.LOCAL_BASE_DIR, false);
        System.out.println("Resultado: " + ok4);

        System.out.println("\n=== Teste 5: Tentativa com host inválido ===");
        RemoteTransferClient sftpInvalido = new SFTP("host.invalido.com", 22, "usuario", "senha");
        boolean ok5 = sftpInvalido.moveToLocal(ConfigPaths.REMOTE_FILE_INVALID, ConfigPaths.LOCAL_BASE_DIR, true);
        System.out.println("Resultado: " + ok5);

        sftp.disconnect();
        sftpInvalido.disconnect();

        System.out.println("\n === Iniciando Teste FTPS ===");
        createLocalDirectoriesAndFiles();

        String hostFTPS = "localhost";    // localhost do Windows, porta mapeada pelo Docker
        int portFTPS = 21;
        String usernameFTPS = "testuser";
        String passwordFTPS = "testpass123!";

        RemoteTransferClient ftps = new FTPS(hostFTPS, portFTPS, usernameFTPS, passwordFTPS);

        // Teste upload - envia os arquivos locais para o servidor
        System.out.println("=== Teste Upload: Enviar arquivos locais para SFTP ===");
        boolean uploadFTPSOk = ftps.moveToRemote(ConfigPaths.LOCAL_BASE_DIR, "/upload", true);
        System.out.println("Resultado upload pasta base: " + uploadOk);

        boolean uploadLogsFTPSOk = ftps.moveToRemote(ConfigPaths.LOCAL_LOGS_DIR, "/upload/logs", true);
        System.out.println("Resultado upload pasta logs: " + uploadLogsOk);

        // Testes download
        System.out.println("\n=== Teste 6: Download de arquivo único ===");
        boolean ok6 = ftps.moveToLocal(ConfigPaths.REMOTE_SINGLE_FILE, ConfigPaths.LOCAL_BASE_DIR, true);
        System.out.println("Resultado: " + ok1);

        System.out.println("\n=== Teste 7: Download de arquivos com wildcard *.csv ===");
        boolean ok7 = ftps.moveToLocal(ConfigPaths.REMOTE_CSV_FILES, ConfigPaths.LOCAL_BASE_DIR, true);
        System.out.println("Resultado: " + ok2);

        System.out.println("\n=== Teste 8: Download recursivo de tudo na pasta /upload/logs ===");
        boolean ok8 = ftps.moveToLocal(ConfigPaths.REMOTE_LOGS_DIR, ConfigPaths.LOCAL_LOGS_DIR, true);
        System.out.println("Resultado: " + ok3);

        System.out.println("\n=== Teste 9: Ignorar operação (isMain = false) ===");
        boolean ok9 = ftps.moveToLocal(ConfigPaths.REMOTE_IGNORE_TEST, ConfigPaths.LOCAL_BASE_DIR, false);
        System.out.println("Resultado: " + ok4);

        System.out.println("\n=== Teste 10: Tentativa com host inválido ===");
        RemoteTransferClient ftpsInvalido = new FTPS("host.invalido.com", 22, "usuario", "senha");
        boolean ok10 = ftpsInvalido.moveToLocal(ConfigPaths.REMOTE_FILE_INVALID, ConfigPaths.LOCAL_BASE_DIR, true);
        System.out.println("Resultado: " + ok5);
    }

    private static void createLocalDirectoriesAndFiles() {
        // Base dir
        File baseDir = new File(ConfigPaths.LOCAL_BASE_DIR);
        if (!baseDir.exists()) baseDir.mkdirs();

        // Logs dir
        File logsDir = new File(ConfigPaths.LOCAL_LOGS_DIR);
        if (!logsDir.exists()) logsDir.mkdirs();

        // Arquivo texto simples
        createFileWithContent(new File(baseDir, "arquivo.txt"), "Conteúdo do arquivo.txt para upload.");

        // Arquivos CSV
        createFileWithContent(new File(baseDir, "dados1.csv"), "col1,col2\n1,2\n3,4");
        createFileWithContent(new File(baseDir, "dados2.csv"), "nome,idade\nJoao,30\nMaria,25");

        // Arquivos na pasta logs
        createFileWithContent(new File(logsDir, "log1.txt"), "Log linha 1\nLog linha 2");
        createFileWithContent(new File(logsDir, "log2.txt"), "Outro log linha 1\nOutro log linha 2");
    }

    private static void createFileWithContent(File file, String content) {
        try {
            if (!file.exists()) {
                if (file.createNewFile()) {
                    try (FileWriter fw = new FileWriter(file)) {
                        fw.write(content);
                    }
                    System.out.println("Arquivo criado: " + file.getAbsolutePath());
                } else {
                    System.err.println("Falha ao criar arquivo: " + file.getAbsolutePath());
                }
            } else {
                System.out.println("Arquivo já existe: " + file.getAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("Erro ao criar arquivo " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    private static void createRecursiveLocalStructure() {
        String base = ConfigPaths.LOCAL_BASE_DIR;
        File subDir1 = new File(base, "subdir1");
        File subDir2 = new File(subDir1, "subdir2");
        subDir2.mkdirs();

        createFileWithContent(new File(subDir1, "file_subdir1.txt"), "Conteúdo arquivo em subdir1");
        createFileWithContent(new File(subDir2, "file_subdir2.txt"), "Conteúdo arquivo em subdir2");

        System.out.println("Estrutura recursiva criada em " + base);
    }

    private static void printDirectoryTree(File folder, String indent) {
        if (folder.isDirectory()) {
            System.out.println(indent + "[DIR] " + folder.getName());
            File[] files = folder.listFiles();
            if (files != null) {
                for (File f : files) {
                    printDirectoryTree(f, indent + "  ");
                }
            }
        } else {
            System.out.println(indent + folder.getName());
        }
    }
}
