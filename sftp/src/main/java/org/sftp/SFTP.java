package org.sftp;

import com.jcraft.jsch.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.Vector;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

public class SFTP implements RemoteTransferClient {
    private final String host;
    private final int port;
    private final String username;
    private final String password;

    private Session session;
    private ChannelSftp channelSftp;
    private boolean isConnected = false;

    public SFTP(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public boolean connect() {
        if (isConnected) return true;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, host, port);
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            session.connect();

            Channel channel = session.openChannel("sftp");
            channel.connect();
            channelSftp = (ChannelSftp) channel;

            isConnected = true;
            System.out.println("Conectado ao servidor SFTP.");
            return true;

        } catch (Exception e) {
            System.err.println("Erro ao conectar ao servidor SFTP: " + e.getMessage());
            e.printStackTrace();
            isConnected = false;
            return false;
        }
    }

    public void disconnect() {
        if (channelSftp != null && channelSftp.isConnected()) {
            channelSftp.disconnect();
            System.out.println("Canal SFTP desconectado.");
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
            System.out.println("Sessão SFTP desconectada.");
        }
        isConnected = false;
    }

    /**
     * Baixa arquivos do servidor SFTP para o sistema local.
     *
     * Pode receber caminho remoto com wildcard '*' para baixar vários arquivos.
     * Remove os arquivos remotos baixados.
     *
     * @param source Caminho remoto no servidor SFTP (exemplo: "/upload/*.csv").
     * @param target Caminho local onde os arquivos serão salvos (exemplo: "C:/dados/").
     * @param isMain Flag para permitir (true) ou ignorar (false) a operação.
     * @return true se pelo menos um arquivo foi baixado com sucesso; false caso contrário.
     */
    public boolean moveToLocal(String source, String target, boolean isMain) {
        if (!isMain) {
            System.out.println("Operação ignorada: isMain = false");
            return false;
        }

        try {
            if (!connect()) {
                System.err.println("Falha ao conectar.");
                return false;
            }

            source = normalizePath(source);
            target = normalizePath(target);

            boolean hasWildcard = source.contains("*");
            String remoteBaseDir;
            Pattern pattern;

            if (hasWildcard) {
                int lastSlash = source.lastIndexOf("/");
                remoteBaseDir = (lastSlash >= 0) ? source.substring(0, lastSlash) : ".";
                String filePattern = source.substring(lastSlash + 1)
                        .replace(".", "\\.")
                        .replace("*", ".*");
                pattern = Pattern.compile(filePattern);
            } else {
                int lastSlash = source.lastIndexOf("/");
                remoteBaseDir = (lastSlash >= 0) ? source.substring(0, lastSlash) : ".";
                String fileName = source.substring(lastSlash + 1);
                pattern = Pattern.compile(Pattern.quote(fileName));
            }

            File localTarget = new File(target);
            if (!localTarget.exists() && !localTarget.mkdirs()) {
                System.err.println("Falha ao criar diretório local base: " + localTarget.getAbsolutePath());
                return false;
            }
            if (localTarget.isFile()) {
                localTarget = localTarget.getParentFile();
            }

            boolean anySuccess = downloadRecursive(remoteBaseDir, localTarget, pattern);
            return anySuccess;
        } catch (Exception e) {
            System.err.println("Erro geral moveToLocal: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean downloadRecursive(String remoteDir, File localDir, Pattern pattern) {
        try {
            if (!localDir.exists() && !localDir.mkdirs()) {
                System.err.println("Erro ao criar diretório local: " + localDir.getAbsolutePath());
                return false;
            }

            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = channelSftp.ls(remoteDir);

            boolean success = false;

            for (ChannelSftp.LsEntry entry : entries) {
                String name = entry.getFilename();
                if (name.equals(".") || name.equals("..")) continue;

                String remotePath = remoteDir + "/" + name;
                File localPath = new File(localDir, name);

                if (entry.getAttrs().isDir()) {
                    boolean childSuccess = downloadRecursive(remotePath, localPath, pattern);
                    success = success || childSuccess;
                } else {
                    if (pattern.matcher(name).matches()) {
                        try (OutputStream os = new FileOutputStream(localPath)) {
                            channelSftp.get(remotePath, os);
                            System.out.println("Download concluído: " + remotePath + " → " + localPath.getAbsolutePath());
                            channelSftp.rm(remotePath);
                            success = true;
                        } catch (Exception e) {
                            System.err.println("Erro ao transferir: " + remotePath + " → " + e.getMessage());
                        }
                    }
                }
            }
            return success;

        } catch (Exception e) {
            System.err.println("Erro no download recursivo: " + e.getMessage());
            return false;
        }
    }

    /**
     * Envia arquivos ou diretórios do sistema local para o servidor SFTP.
     *
     * Suporta caminho local com wildcard '*' para enviar múltiplos arquivos.
     * Cria diretórios remotos caso não existam.
     *
     * @param source Caminho local do arquivo ou diretório (exemplo: "C:/upload/*.txt").
     * @param target Caminho remoto onde os arquivos serão enviados (exemplo: "/dados/").
     * @param isMain Flag para permitir (true) ou ignorar (false) a operação.
     * @return true se pelo menos um arquivo foi enviado com sucesso; false caso contrário.
     */
    public boolean moveToRemote(String source, String target, boolean isMain) {
        if (!isMain) {
            System.out.println("Operação ignorada: isMain = false");
            return false;
        }

        try {
            if (!connect()) {
                System.err.println("Falha ao conectar.");
                return false;
            }

            source = normalizePath(source);
            target = normalizePath(target);

            File localSource = new File(source);
            if (!localSource.exists()) {
                System.err.println("Arquivo ou diretório local não existe: " + source);
                return false;
            }

            // Remove barras extras e normaliza target
            target = target.replaceAll("/+", "/");
            boolean targetIsDir = target.endsWith("/");

            // Função para garantir que o diretório remoto exista
            String finalTarget = target;
            Runnable ensureRemoteDirectories = () -> {
                try {
                    String[] folders = finalTarget.split("/");
                    String currentPath = "";
                    for (String folder : folders) {
                        if (folder.isEmpty()) continue;
                        currentPath += "/" + folder;
                        try {
                            channelSftp.cd(currentPath);
                        } catch (SftpException e) {
                            try {
                                channelSftp.mkdir(currentPath);
                                System.out.println("Criado diretório remoto: " + currentPath);
                            } catch (SftpException ex) {
                                System.err.println("Erro ao criar diretório remoto " + currentPath + ": " + ex.getMessage());
                                throw ex; // interrompe criação de diretórios
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Erro criando diretórios remotos: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            };

            boolean anySuccess = false;
            if (localSource.isDirectory()) {
                // Se target é um diretório remoto, remove barras finais para evitar path errado
                String remoteDir = targetIsDir ? target.replaceAll("/+$", "") : target;
                anySuccess = uploadRecursive(localSource, remoteDir);
            } else if (source.contains("*")) {
                // Upload com wildcard local
                int lastSlash = source.lastIndexOf("/");
                String localDirPath = (lastSlash >= 0) ? source.substring(0, lastSlash) : ".";
                String patternText = source.substring(lastSlash + 1)
                        .replace(".", "\\.")
                        .replace("*", ".*");
                Pattern pattern = Pattern.compile(patternText);

                File localDir = new File(localDirPath);
                if (!localDir.exists() || !localDir.isDirectory()) {
                    System.err.println("Diretório local para wildcard inválido: " + localDirPath);
                    return false;
                }

                ensureRemoteDirectories.run();

                File[] files = localDir.listFiles((dir, name) -> pattern.matcher(name).matches());
                if (files != null) {
                    for (File f : files) {
                        String remoteFilePath = targetIsDir ? target + f.getName() : target;
                        try (var fis = new java.io.FileInputStream(f)) {
                            channelSftp.put(fis, remoteFilePath);
                            System.out.println("Upload concluído: " + f.getAbsolutePath() + " → " + remoteFilePath);
                            anySuccess = true;
                        } catch (Exception e) {
                            System.err.println("Erro no upload do arquivo: " + f.getAbsolutePath() + " → " + e.getMessage());
                        }
                    }
                }
            } else {
                ensureRemoteDirectories.run();
                String remoteFilePath = targetIsDir ? target + localSource.getName() : target;
                try (var fis = new java.io.FileInputStream(localSource)) {
                    channelSftp.put(fis, remoteFilePath);
                    System.out.println("Upload concluído: " + localSource.getAbsolutePath() + " → " + remoteFilePath);
                    anySuccess = true;
                } catch (Exception e) {
                    System.err.println("Erro no upload do arquivo: " + e.getMessage());
                }
            }

            return anySuccess;

        } catch (Exception e) {
            System.err.println("Erro geral moveToSFTP: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean uploadRecursive(File localFile, String remotePath) {
        boolean success = false;
        try {
            if (localFile.isDirectory()) {
                try {
                    channelSftp.cd(remotePath);
                } catch (SftpException e) {
                    try {
                        channelSftp.mkdir(remotePath);
                        System.out.println("Criado diretório remoto: " + remotePath);
                    } catch (SftpException ex) {
                        System.err.println("Erro criando diretório remoto: " + ex.getMessage());
                        return false;}
                }
                File[] files = localFile.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String remoteSubPath = remotePath + "/" + f.getName();
                        boolean childSuccess = uploadRecursive(f, remoteSubPath);
                        success = success || childSuccess;
                    }
                }
            } else {
                try (var fis = new java.io.FileInputStream(localFile)) {
                    channelSftp.put(fis, remotePath);
                    System.out.println("Upload concluído: " + localFile.getAbsolutePath() + " → " + remotePath);
                    success = true;
                } catch (Exception e) {
                    System.err.println("Erro no upload do arquivo: " + localFile.getAbsolutePath() + " → " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Erro no upload recursivo: " + e.getMessage());
        }
        return success;
    }

    /**
     * Normaliza um caminho trocando '\' por '/' e removendo barras extras.
     *
     * @param path Caminho a ser normalizado.
     * @return Caminho normalizado com '/' e sem barras duplicadas.
     */
    private String normalizePath(String path) {
        return path.replace("\\", "/").replaceAll("/+", "/").trim();
    }

    /**
     * Cria diretório local caso não exista, exibindo logs de sucesso ou falha.
     *
     * @param dir Diretório local a ser criado.
     */
    private void createLocalDirectory(File dir) {
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                System.out.println("Diretório local criado: " + dir.getAbsolutePath());
            } else {
                System.err.println("Erro ao criar diretório local: " + dir.getAbsolutePath());
            }
        }
    }

    public boolean isConnected() {
        return isConnected;
    }
}
