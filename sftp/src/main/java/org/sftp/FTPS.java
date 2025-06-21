package org.sftp;

import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.PrintCommandListener;

import java.io.*;
import java.util.regex.Pattern;

/**
 * Cliente FTPS (FTP sobre TLS/SSL) com métodos equivalentes à classe SFTP.
 *
 * Suporta:
 * - Conexão/Desconexão
 * - Download com wildcard e recursão, removendo arquivos remotos
 * - Upload com wildcard e recursão, criando diretórios remotos
 */
public class FTPS implements RemoteTransferClient {
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private FTPSClient client;
    private boolean isConnected = false;

    /**
     * Construtor da classe FTPS.
     *
     * @param host     Servidor FTPS (hostname ou IP)
     * @param port     Porta (normalmente 21 ou 990)
     * @param username Usuário FTP
     * @param password Senha do usuário
     */
    public FTPS(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.client = new FTPSClient();
        this.client.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));
    }

    /**
     * Estabelece conexão TLS com o servidor.
     *
     * @return true em caso de conexão bem sucedida
     * @throws IOException em caso de erro de rede
     */
    public boolean connect() {
        System.out.printf("[FTPS] Conectando a %s:%d como '%s'%n", host, port, username);

        try {
            client.connect(host, port);

            if (!FTPReply.isPositiveCompletion(client.getReplyCode())) {
                System.err.println("[FTPS] Conexão recusada pelo servidor.");
                client.disconnect();
                return false;
            }

            if (!client.login(username, password)) {
                System.err.println("[FTPS] Falha no login.");
                client.disconnect();
                return false;
            }

            // Configura a proteção TLS ANTES do login
            client.execPBSZ(0);
            client.execPROT("P");
            client.enterLocalPassiveMode();
            client.setFileType(FTPSClient.BINARY_FILE_TYPE);

            isConnected = true;
            System.out.println("[FTPS] Conectado com sucesso.");
            return true;

        } catch (IOException e) {
            System.err.println("[FTPS] Erro ao conectar: " + e.getMessage());
            e.printStackTrace();

            try {
                if (client.isConnected()) {
                    client.disconnect();
                }
            } catch (IOException ex) {
                System.err.println("[FTPS] Erro ao desconectar após falha de conexão: " + ex.getMessage());
            }

            isConnected = false;
            return false;
        }
    }

    /**
     * Finaliza sessão FTPS, desconectando o cliente.
     *
     * @throws IOException em caso de erro no encerramento
     */
    public void disconnect() {
        if (!isConnected) return;

        try {
            client.logout();
            client.disconnect();
            isConnected = false;
            System.out.println("[FTPS] Desconectado.");
        } catch (IOException e) {
            System.err.println("[FTPS] Erro ao desconectar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Baixa arquivos do FTPS para o sistema local.
     * Suporta wildcard e recursão. Remove arquivos remotos ao finalizar o download.
     *
     * @param source Caminho remoto (ex: "/upload/*.csv")
     * @param target Diretório local de destino (ex: "C:/dados")
     * @param isMain Permite execução se true, ignora se false
     * @return true se ao menos um arquivo foi baixado
     * @throws IOException
     */
    public boolean moveToLocal(String source, String target, boolean isMain) {
        if (!isMain) {
            System.out.println("Operação ignorada: isMain = false");
            return false;
        }

        try {
            if (!connect()) {
                System.err.println("Falha ao conectar ao servidor remoto.");
                return false;
            }

            ensureLocalDir(target);
            String dir = source.contains("*") ? source.substring(0, source.lastIndexOf("/")) : source;
            String namePattern = source.substring(source.lastIndexOf("/") + 1)
                    .replace(".", "\\.").replace("*", ".*");
            Pattern pattern = Pattern.compile(namePattern);

            boolean result = downloadRecursive(dir, new File(target), pattern);
            return result;

        } catch (Exception e) {
            System.err.println("Erro em moveToRemote: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Envia arquivos locais para o FTPS.
     * Suporta wildcard e recursão. Cria diretórios remotos conforme necessário.
     *
     * @param source Caminho local (ex: "C:/upload/*.txt")
     * @param target Caminho remoto (ex: "/dados/")
     * @param isMain Permite execução se true, ignora se false
     * @return true se ao menos um arquivo foi enviado
     * @throws IOException
     */
    public boolean moveToRemote(String source, String target, boolean isMain) {
        if (!isMain) return false;

        if (!connect()) return false;

        boolean success = false;
        try {
            ensureRemoteDirs(target);
        } catch (IOException e) {
            System.err.println("[FTPS] Diretorio não pode ser criado: " + e.getMessage());
            e.printStackTrace();
            success = false;

        }

        File local = new File(source);
        try {
            if (local.isDirectory()) {
                success = uploadRecursive(local, target);
            } else if (source.contains("*")) {
                File dir = local.getParentFile();
                Pattern p = Pattern.compile(local.getName().replace(".", "\\.").replace("*", ".*"));
                File[] files = dir.listFiles((d, name) -> p.matcher(name).matches());
                if (files != null) {
                    for (File f : files) {
                        try (FileInputStream fis = new FileInputStream(f)) {
                            boolean fileSuccess = client.storeFile(target + f.getName(), fis);
                            success |= fileSuccess;
                            System.out.printf("[FTPS] Upload %s → %s : %s%n", f.getAbsolutePath(), target + f.getName(), fileSuccess);
                        } catch (IOException e) {
                            System.err.printf("[FTPS] Erro no upload do arquivo %s: %s%n", f.getAbsolutePath(), e.getMessage());
                        }
                    }
                }
            } else {
                try (FileInputStream fis = new FileInputStream(local)) {
                    success = client.storeFile(target + local.getName(), fis);
                } catch (IOException e) {
                    System.err.printf("[FTPS] Erro no upload do arquivo %s: %s%n", local.getAbsolutePath(), e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[FTPS] Erro geral no upload: " + e.getMessage());
            e.printStackTrace();
            success = false;
        }

        System.out.println("[FTPS] Upload finalizado com sucesso? " + success);
        return success;
    }

    private void ensureLocalDir(String remote) {
        String[] parts = remote.split("/");
        String path = "";
        for (String p : parts) {
            if (p.isEmpty()) continue;
            path += "/" + p;
            try {
                if (!client.changeWorkingDirectory(path)) {
                    if (client.makeDirectory(path)) {
                        System.out.println("[FTPS] Criado remoto: " + path);
                    } else {
                        System.err.println("[FTPS] Falha ao criar diretório remoto: " + path);
                    }
                }
            } catch (IOException e) {
                System.err.println("[FTPS] Erro ao acessar/criar diretório remoto '" + path + "': " + e.getMessage());
            }
        }
    }

    private boolean downloadRecursive(String remoteDir, File localDir, Pattern pattern) throws IOException {
        boolean anySuccess = false;
        for (FTPFile f : client.listFiles(remoteDir)) {
            if (".".equals(f.getName())|| "..".equals(f.getName())) continue;
            String remotePath = remoteDir + "/" + f.getName();
            File localTarget = new File(localDir, f.getName());

            if (f.isDirectory()) {
                ensureLocalDir(localTarget.getAbsolutePath());
                anySuccess |= downloadRecursive(remotePath, localTarget, pattern);
            } else if (pattern.matcher(f.getName()).matches()) {
                boolean ok = client.retrieveFile(remotePath, new FileOutputStream(localTarget));
                System.out.printf("[FTPS] Download %s → %s : %s%n", remotePath, localTarget, ok);
                if (ok) {
                    client.deleteFile(remotePath);
                    System.out.println("[FTPS] Deletado remoto: " + remotePath);
                    anySuccess = true;
                }
            }
        }
        return anySuccess;
    }

    private void ensureRemoteDirs(String remote) throws IOException {
        String[] parts = remote.split("/");
        String path = "";
        for (String p : parts) {
            if (p.isEmpty()) continue;
            path += "/" + p;
            if (!client.changeWorkingDirectory(path)) {
                client.makeDirectory(path);
                System.out.println("[FTPS] Criado remoto: " + path);
            }
        }
    }

    private boolean uploadRecursive(File localDir, String remoteDir) throws IOException {
        boolean anySuccess = false;
        client.changeWorkingDirectory(remoteDir);

        for (File f : localDir.listFiles()) {
            String remotePath = remoteDir + "/" + f.getName();
            if (f.isDirectory()) {
                ensureRemoteDirs(remotePath);
                anySuccess |= uploadRecursive(f, remotePath);
            } else {
                boolean ok = client.storeFile(remotePath, new FileInputStream(f));
                System.out.printf("[FTPS] Upload %s → %s : %s%n", f, remotePath, ok);
                anySuccess |= ok;
            }
        }
        return anySuccess;
    }

    public boolean isConnected() {
        return isConnected;
    }
}
